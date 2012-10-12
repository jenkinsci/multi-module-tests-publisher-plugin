/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, id:cactusman, Tom Huybrechts, Yahoo!, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cwctravel.hudson.plugins.suitegroupedtests.junit;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.History;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import com.cwctravel.hudson.plugins.suitegroupedtests.SuiteGroupResultAction;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitMetricsInfo;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitSummaryInfo;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitTestDetailInfo;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitTestInfo;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.io.IOUtil;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.io.StringReaderWriter;

/**
 * Root of all the test results for one build.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class TestResult extends MetaTabulatedResult {
	private static final Logger LOGGER = Logger.getLogger(TestResult.class.getName());
	private static final long serialVersionUID = 1L;

	private int failedSince;

	private TestObject parent;

	private final JUnitDB junitDB;

	private final JUnitSummaryInfo summary;
	private JUnitSummaryInfo previousSummary;

	private JUnitMetricsInfo metrics;

	/**
	 * Creates an empty result.
	 */

	public TestResult(TestObject parent, JUnitSummaryInfo junitSummaryInfo) {
		this.parent = parent;
		this.summary = junitSummaryInfo;
		try {
			this.junitDB = new JUnitDB(getOwner().getProject().getRootDir().getAbsolutePath());
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	@Override
	public TestObject getParent() {
		return parent;
	}

	@Override
	public void setParent(TestObject parent) {
		this.parent = parent;
	}

	public String getDisplayName() {
		return summary.getSuiteName();
	}

	@Override
	public AbstractBuild<?, ?> getOwner() {
		return(parent == null ? null : parent.getOwner());
	}

	@Override
	public SuiteGroupResultAction getTestResultAction() {
		return (SuiteGroupResultAction)super.getTestResultAction();
	}

	@Override
	public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
		if(getId().equals(id) || (id == null)) {
			return this;
		}

		String firstElement = null;
		String subId = null;
		int sepIndex = id.indexOf('/');
		if(sepIndex < 0) {
			firstElement = id;
			subId = null;
		}
		else {
			firstElement = id.substring(0, sepIndex);
			subId = id.substring(sepIndex + 1);
			if(subId.length() == 0) {
				subId = null;
			}
		}

		String packageName = null;
		if(firstElement.equals(getId())) {
			sepIndex = subId.indexOf('/');
			if(sepIndex < 0) {
				packageName = subId;
				subId = null;
			}
			else {
				packageName = subId.substring(0, sepIndex);
				subId = subId.substring(sepIndex + 1);
			}
		}
		else {
			packageName = firstElement;
			subId = null;
		}
		PackageResult child = byPackage(packageName);
		if(child != null) {
			if(subId != null) {
				return child.findCorrespondingResult(subId);
			}
			else {
				return child;
			}
		}
		else {
			return null;
		}
	}

	@Override
	public String getTitle() {
		return Messages.TestResult_getTitle(getName());
	}

	@Override
	public String getChildTitle() {
		return Messages.TestResult_getChildTitle();
	}

	@Exported(visibility = 999)
	@Override
	public float getDuration() {
		return (float)summary.getDuration() / 1000;
	}

	@Exported(visibility = 999)
	@Override
	public int getPassCount() {
		return (int)summary.getPassCount();
	}

	@Exported(visibility = 999)
	@Override
	public int getFailCount() {
		return (int)(summary.getFailCount() + summary.getErrorCount());
	}

	@Exported(visibility = 999)
	@Override
	public int getSkipCount() {
		return (int)summary.getSkipCount();
	}

	public int getPassDiff() {
		JUnitSummaryInfo junitSummaryInfo = getPreviousSummary();

		if(junitSummaryInfo != null) {
			return getPassCount() - (int)junitSummaryInfo.getPassCount();
		}
		else {
			return getPassCount();
		}
	}

	public int getSkipDiff() {
		JUnitSummaryInfo junitSummaryInfo = getPreviousSummary();

		if(junitSummaryInfo != null) {
			return getSkipCount() - (int)junitSummaryInfo.getSkipCount();
		}
		else {
			return getSkipCount();
		}
	}

	public int getFailDiff() {
		JUnitSummaryInfo junitSummaryInfo = getPreviousSummary();

		if(junitSummaryInfo != null) {
			return getFailCount() - (int)junitSummaryInfo.getFailCount();
		}
		else {
			return getFailCount();
		}
	}

	public int getTotalDiff() {
		JUnitSummaryInfo junitSummaryInfo = getPreviousSummary();
		if(junitSummaryInfo != null) {
			return getTotalCount() - (int)junitSummaryInfo.getTotalCount();
		}
		else {
			return getTotalCount();
		}
	}

	public JUnitMetricsInfo getMetrics() {
		if(metrics == null) {
			try {
				metrics = junitDB.fetchTestSuiteMetrics(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return metrics;
	}

	@Override
	public TestResult getPreviousResult() {
		return new TestResult(getParent(), getPreviousSummary());
	}

	private JUnitSummaryInfo getPreviousSummary() {
		if(previousSummary == null) {
			try {
				previousSummary = junitDB.summarizeTestSuiteForBuildPriorTo(summary.getBuildNumber(), summary.getProjectName(), getName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return previousSummary;
	}

	@Override
	public List<CaseResult> getFailedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsBySuite(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_FAIL || junitTestInfo.getStatus() == JUnitTestInfo.STATUS_ERROR) {
					CaseResult caseResult = new CaseResult(this, junitTestInfo);
					result.add(caseResult);
				}
			}
			return result;
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	/**
	 * Gets the "children" of this test result that passed
	 * 
	 * @return the children of this test result, if any, or an empty collection
	 */
	@Override
	public Collection<? extends hudson.tasks.test.TestResult> getPassedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsBySuite(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SUCCESS) {
					CaseResult caseResult = new CaseResult(this, junitTestInfo);
					result.add(caseResult);
				}
			}
			return result;
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	/**
	 * Gets the "children" of this test result that were skipped
	 * 
	 * @return the children of this test result, if any, or an empty list
	 */
	@Override
	public Collection<? extends hudson.tasks.test.TestResult> getSkippedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsBySuite(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SKIP) {
					CaseResult caseResult = new CaseResult(this, junitTestInfo);
					result.add(caseResult);
				}
			}
			return result;
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	@Override
	public int getFailedSince() { // TODO: implement this.
		if(failedSince == 0 && getFailCount() > 0) {
			try {
				List<JUnitSummaryInfo> history = junitDB.summarizeTestSuiteHistory(summary.getProjectName(), summary.getSuiteName(), 5000);
				for(JUnitSummaryInfo junitSummaryInfo: history) {
					int failedBuildNumber = junitSummaryInfo.getBuildNumber();
					if(failedBuildNumber < getOwner().getNumber() && junitSummaryInfo.getFailCount() > 0) {
						failedSince = failedBuildNumber;
						break;
					}
				}
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return failedSince;
	}

	@Override
	public Run<?, ?> getFailedSinceRun() { // TODO: implement this.
		return getOwner().getParent().getBuildByNumber(getFailedSince());
	}

	/**
	 * The stdout of this test.
	 * <p/>
	 * <p/>
	 * Depending on the tool that produced the XML report, this method works somewhat inconsistently. With some tools (such as Maven surefire plugin),
	 * you get the accurate information, that is the stdout from this test case. With some other tools (such as the JUnit task in Ant), this method
	 * returns the stdout produced by the entire test suite.
	 * <p/>
	 * <p/>
	 * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
	 * 
	 * @since 1.294
	 */
	@Override
	public String getStdout() {
		StringReaderWriter stdoutReaderWriter = new StringReaderWriter();
		String result = "";
		try {
			JUnitTestDetailInfo junitTestDetailInfo = junitDB.readTestDetail(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName(), "<init>", "<init>", "<init>", stdoutReaderWriter, null);
			StringWriter sW = new StringWriter();
			IOUtil.write(junitTestDetailInfo.getStdout(), sW);
			result = sW.toString();
		}
		catch(SQLException sE) {
			LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
		}
		catch(IOException iE) {
			LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
		}
		finally {
			try {
				stdoutReaderWriter.release();
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
		}

		return result;
	}

	/**
	 * The stderr of this test.
	 * 
	 * @see #getStdout()
	 * @since 1.294
	 */
	@Override
	public String getStderr() {
		StringReaderWriter stderrReaderWriter = new StringReaderWriter();
		String result = "";
		try {
			JUnitTestDetailInfo junitTestDetailInfo = junitDB.readTestDetail(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName(), "<init>", "<init>", "<init>", null, stderrReaderWriter);
			StringWriter sW = new StringWriter();
			IOUtil.write(junitTestDetailInfo.getStderr(), sW);
			result = sW.toString();

		}
		catch(SQLException sE) {
			LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
		}
		catch(IOException iE) {
			LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
		}
		finally {
			try {
				stderrReaderWriter.release();
			}
			catch(IOException iE) {
				LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
			}
		}

		return result;
	}

	/**
	 * If there was an error or a failure, this is the stack trace, or otherwise null.
	 */
	@Override
	public String getErrorStackTrace() {
		return "No error stack traces available at this level. Drill down to individual tests to find stack traces.";
	}

	/**
	 * If there was an error or a failure, this is the text from the message.
	 */
	@Override
	public String getErrorDetails() {
		return "No error details available at this level. Drill down to individual tests to find details.";
	}

	/**
	 * @return true if the test was not skipped and did not fail, false otherwise.
	 */
	@Override
	public boolean isPassed() {
		return(getFailCount() == 0);
	}

	@Override
	public Collection<PackageResult> getChildren() {
		try {
			List<PackageResult> result = new ArrayList<PackageResult>();
			List<JUnitSummaryInfo> junitSummaryInfoList = junitDB.fetchTestSuiteChildrenForBuild(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName());
			for(JUnitSummaryInfo summaryInfo: junitSummaryInfoList) {
				PackageResult packageResult = new PackageResult(this, summaryInfo);
				result.add(packageResult);
			}

			return result;
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	/**
	 * Whether this test result has children.
	 */
	@Override
	public boolean hasChildren() {
		return summary.getTotalCount() == 0;
	}

	@Override
	public String getName() {
		return summary.getSuiteName();
	}

	@Override
	public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
		if(token.equals(getId())) {
			return this;
		}

		PackageResult result = byPackage(token);
		if(result != null) {
			return result;
		}
		else {
			return super.getDynamic(token, req, rsp);
		}
	}

	public PackageResult byPackage(String packageName) {
		try {
			PackageResult result = null;
			JUnitSummaryInfo junitSummaryInfo = junitDB.summarizeTestPackageForBuild(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName(), packageName);
			if(junitSummaryInfo != null) {
				result = new PackageResult(this, junitSummaryInfo);
			}
			return result;
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	public String getRootUrl(String urlName) {
		return getTestResultAction().getRootUrl(this, urlName);
	}

	public String getRootUrl(TestAction testAction) {
		return getTestResultAction().getRootUrl(this, testAction);
	}

	@Override
	public List<TestAction> getTestActions() {
		return SuiteGroupResultAction.getTestActions(this, getTestResultAction());

	}

	@Override
	public History getHistory() {
		return new com.cwctravel.hudson.plugins.suitegroupedtests.junit.History(this, 5000);
	}

}
