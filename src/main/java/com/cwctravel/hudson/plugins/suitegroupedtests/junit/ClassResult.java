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
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.History;
import hudson.tasks.test.TabulatedResult;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResult;

import java.sql.SQLException;
import java.util.ArrayList;
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
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitTestInfo;

/**
 * Cumulative test result of a test class.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class ClassResult extends TabulatedResult implements Comparable<ClassResult> {
	private static final Logger LOGGER = Logger.getLogger(ClassResult.class.getName());

	private int failedSince;

	private JUnitDB junitDB;

	private final TestObject parent;

	private final JUnitSummaryInfo summary;
	private JUnitSummaryInfo previousSummary;

	private JUnitMetricsInfo metrics;

	public ClassResult(TestObject parent, JUnitSummaryInfo summary) {
		this.parent = parent;
		this.summary = summary;
		try {
			junitDB = new JUnitDB(getOwner().getProject().getRootDir().getAbsolutePath());
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	@Override
	public AbstractBuild<?, ?> getOwner() {
		return(parent == null ? null : parent.getOwner());
	}

	@Override
	public TestObject getParent() {
		return parent;
	}

	@Override
	public List<TestAction> getTestActions() {
		return SuiteGroupResultAction.getTestActions(this, getTestResultAction());
	}

	@Override
	public SuiteGroupResultAction getTestResultAction() {
		return (SuiteGroupResultAction)super.getTestResultAction();
	}

	@Override
	public ClassResult getPreviousResult() {
		return new ClassResult(getParent(), getPreviousSummary());
	}

	@Override
	public TestResult findCorrespondingResult(String id) {
		String myID = safe(getName());
		int base = id.indexOf(myID);
		String caseName;
		if(base > 0) {
			int caseNameStart = base + myID.length() + 1;
			caseName = id.substring(caseNameStart);
		}
		else {
			caseName = id;
		}

		CaseResult child = getCaseResult(caseName);
		if(child != null) {
			return child;
		}

		return null;
	}

	@Override
	public String getTitle() {
		return Messages.ClassResult_getTitle(getParent().getParent().getDisplayName(), getParent().getName(), getName());
	}

	@Override
	public String getChildTitle() {
		return "Class Reults";
	}

	@Override
	@Exported(visibility = 999)
	public String getName() {
		String className = getClassName();
		int idx = className.lastIndexOf('.');
		if(idx < 0)
			return className;
		else
			return className.substring(idx + 1);
	}

	public @Override
	String getSafeName() {
		return safe(getName());
	}

	public CaseResult getCaseResult(String name) {
		CaseResult result = null;
		try {
			JUnitTestInfo junitTestInfo = junitDB.queryTestCase(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName(), name);
			if(junitTestInfo != null) {
				result = new CaseResult(this, junitTestInfo);
			}
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
		return result;
	}

	@Override
	public Object getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
		CaseResult c = getCaseResult(name);
		if(c != null) {
			return c;
		}
		else {
			return super.getDynamic(name, req, rsp);
		}
	}

	@Override
	public List<CaseResult> getFailedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByClass(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName());
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

	@Override
	public List<CaseResult> getSkippedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByClass(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName());
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
	public List<CaseResult> getPassedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByClass(summary.getProjectName(), summary.getBuildId(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName());
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

	@Override
	@Exported(name = "child")
	public List<CaseResult> getChildren() {
		List<CaseResult> result = new ArrayList<CaseResult>();
		try {
			List<JUnitTestInfo> junitTestInfoList = junitDB.fetchTestClassChildrenForBuild(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				CaseResult caseResult = new CaseResult(this, junitTestInfo);
				result.add(caseResult);
			}
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
		return result;
	}

	@Override
	public boolean hasChildren() {
		return summary.getTotalCount() > 0;
	}

	// TODO: wait for stapler 1.60 @Exported
	@Override
	public float getDuration() {
		return (float)summary.getDuration() / 1000;
	}

	@Override
	@Exported
	public int getPassCount() {
		return (int)summary.getPassCount();
	}

	@Override
	@Exported
	public int getFailCount() {
		return (int)(summary.getFailCount() + summary.getErrorCount());
	}

	@Override
	@Exported
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
				metrics = junitDB.fetchTestClassMetrics(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return metrics;
	}

	@Override
	public int getFailedSince() {
		if(failedSince == 0 && getFailCount() > 0) {
			try {
				List<JUnitSummaryInfo> history = junitDB.summarizeTestClassHistory(summary.getProjectName(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName(), 5000);
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
	public Run<?, ?> getFailedSinceRun() {
		return getOwner().getParent().getBuildByNumber(getFailedSince());
	}

	private JUnitSummaryInfo getPreviousSummary() {
		if(previousSummary == null) {
			try {
				previousSummary = junitDB.summarizeTestClassForBuildPriorTo(summary.getBuildNumber(), summary.getProjectName(), summary.getSuiteName(), summary.getPackageName(), summary.getClassName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return previousSummary;
	}

	public String getClassName() {
		return summary.getClassName();
	}

	public int compareTo(ClassResult that) {
		return this.getClassName().compareTo(that.getClassName());
	}

	public String getDisplayName() {
		return getName();
	}

	public String getFullName() {
		return getParent().getDisplayName() + "." + getClassName();
	}

	/**
	 * Gets the relative path to this test case from the given object.
	 */
	@Override
	public String getRelativePathFrom(TestObject it) {
		if(it instanceof CaseResult) {
			return "..";
		}
		else {
			return super.getRelativePathFrom(it);
		}
	}

	public String getRootUrl(String urlName) {
		return getTestResultAction().getRootUrl(this, urlName);
	}

	public String getRootUrl(TestAction testAction) {
		return getTestResultAction().getRootUrl(this, testAction);
	}

	@Override
	public History getHistory() {
		return new com.cwctravel.hudson.plugins.suitegroupedtests.junit.History(this, 5000);
	}
}
