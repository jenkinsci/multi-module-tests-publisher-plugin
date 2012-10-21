/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Seiji Sogabe, Tom Huybrechts, Yahoo!, Inc.
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

import static java.util.Collections.emptyList;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.History;
import hudson.tasks.test.TestObject;
import hudson.tasks.test.TestResult;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jvnet.localizer.Localizable;
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
 * One test result.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class CaseResult extends TestResult implements Comparable<CaseResult> {
	private static final Logger LOGGER = Logger.getLogger(CaseResult.class.getName());

	private int failedSince;

	private final TestObject parent;

	private final JUnitDB junitDB;

	private final JUnitTestInfo testInfo;
	private JUnitTestInfo previousTestInfo;

	private JUnitMetricsInfo metrics;

	public CaseResult(TestObject parent, JUnitTestInfo testInfo) {
		this.parent = parent;
		this.testInfo = testInfo;
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
	public List<TestAction> getTestActions() {
		return SuiteGroupResultAction.getTestActions(this, getTestResultAction());
	}

	@Override
	public SuiteGroupResultAction getTestResultAction() {
		return (SuiteGroupResultAction)super.getTestResultAction();
	}

	public String getDisplayName() {
		return testInfo.getCaseName();
	}

	/**
	 * Gets the name of the test, which is returned from {@code TestCase.getName()}
	 * <p>
	 * Note that this may contain any URL-unfriendly character.
	 */
	@Exported(visibility = 999)
	public @Override
	String getName() {
		return testInfo.getCaseName();
	}

	/**
	 * Gets the human readable title of this result object.
	 */
	@Override
	public String getTitle() {
		return "Case Result: " + getName();
	}

	/**
	 * Gets the duration of the test, in seconds
	 */
	@Override
	@Exported(visibility = 9)
	public float getDuration() {
		return (float)testInfo.getDuration() / 1000;
	}

	/**
	 * Gets the version of {@link #getName()} that's URL-safe.
	 */
	public @Override
	String getSafeName() {
		return safe(getName());
	}

	/**
	 * Gets the class name of a test class.
	 */
	@Exported(visibility = 9)
	public String getClassName() {
		return parent.getName();
	}

	/**
	 * Gets the simple (not qualified) class name.
	 */
	public String getSimpleName() {
		String className = getClassName();
		int idx = className.lastIndexOf('.');
		return className.substring(idx + 1);
	}

	/**
	 * Gets the package name of a test case
	 */
	public String getPackageName() {
		String className = getClassName();
		int idx = className.lastIndexOf('.');
		if(idx < 0)
			return "(root)";
		else
			return className.substring(0, idx);
	}

	public String getFullName() {
		return testInfo.getSuiteName() + '.' + testInfo.getPackageName() + '.' + testInfo.getClassName() + '.' + testInfo.getCaseName();
	}

	@Override
	public int getFailCount() {
		if(!isPassed() && !isSkipped())
			return 1;
		else
			return 0;
	}

	@Override
	public int getSkipCount() {
		if(isSkipped())
			return 1;
		else
			return 0;
	}

	@Override
	public int getPassCount() {
		return isPassed() ? 1 : 0;
	}

	/**
	 * If this test failed, then return the build number when this test started failing.
	 */
	@Override
	@Exported(visibility = 9)
	public int getFailedSince() {
		// If we haven't calculated failedSince yet, and we should,
		// do it now.
		if(failedSince == 0 && getFailCount() > 0) {
			try {
				List<JUnitSummaryInfo> history = junitDB.summarizeTestCaseHistory(testInfo.getProjectName(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName(), 5000);
				for(JUnitSummaryInfo junitSummaryInfo: history) {
					int failedBuildNumber = junitSummaryInfo.getBuildNumber();
					if(failedBuildNumber < testInfo.getBuildNumber() && (junitSummaryInfo.getFailCount() > 0 || junitSummaryInfo.getErrorCount() > 0)) {
						failedSince = failedBuildNumber;
						break;
					}
				}
				if(failedSince == 0) {
					failedSince = testInfo.getBuildNumber();
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

	/**
	 * Gets the number of consecutive builds (including this) that this test case has been failing.
	 */
	@Exported(visibility = 9)
	public int getAge() {
		if(isPassed()) {
			return 0;
		}
		else {
			return testInfo.getBuildNumber() - getFailedSince() + 1;
		}
	}

	/**
	 * The stdout of this test.
	 * <p>
	 * Depending on the tool that produced the XML report, this method works somewhat inconsistently. With some tools (such as Maven surefire plugin),
	 * you get the accurate information, that is the stdout from this test case. With some other tools (such as the JUnit task in Ant), this method
	 * returns the stdout produced by the entire test suite.
	 * <p>
	 * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
	 * 
	 * @since 1.294
	 */
	@Override
	@Exported
	public String getStdout() {
		StringReaderWriter stdoutReaderWriter = new StringReaderWriter();
		String result = "";
		try {
			JUnitTestDetailInfo junitTestDetailInfo = junitDB.readTestDetail(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName(), stdoutReaderWriter, null);
			StringWriter sW = new StringWriter();
			IOUtil.write(junitTestDetailInfo.getStdout(), sW);
			result = sW.toString();
			if(result == null || result.isEmpty()) {
				junitTestDetailInfo = junitDB.readTestDetail(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), "<init>", "<init>", "<init>", stdoutReaderWriter, null);
				sW = new StringWriter();
				IOUtil.write(junitTestDetailInfo.getStdout(), sW);
				result = sW.toString();
			}
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
	@Exported
	public String getStderr() {
		StringReaderWriter stderrReaderWriter = new StringReaderWriter();
		String result = "";
		try {
			JUnitTestDetailInfo junitTestDetailInfo = junitDB.readTestDetail(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName(), null, stderrReaderWriter);
			StringWriter sW = new StringWriter();
			IOUtil.write(junitTestDetailInfo.getStderr(), sW);
			result = sW.toString();
			if(result == null || result.isEmpty()) {
				junitTestDetailInfo = junitDB.readTestDetail(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), "<init>", "<init>", "<init>", null, stderrReaderWriter);
				sW = new StringWriter();
				IOUtil.write(junitTestDetailInfo.getStderr(), sW);
				result = sW.toString();
			}
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

	@Override
	public CaseResult getPreviousResult() {
		return new CaseResult(getParent(), getPreviousTestInfo());
	}

	public JUnitMetricsInfo getMetrics() {
		if(metrics == null) {
			try {
				metrics = junitDB.fetchTestCaseMetrics(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return metrics;
	}

	/**
	 * Case results have no children
	 * 
	 * @return null
	 */
	@Override
	public TestResult findCorrespondingResult(String id) {
		if(id.equals(safe(getName()))) {
			return this;
		}
		return null;
	}

	/**
	 * Gets the "children" of this test result that failed
	 * 
	 * @return the children of this test result, if any, or an empty collection
	 */
	@Override
	public Collection<? extends TestResult> getFailedTests() {
		return singletonListOrEmpty(!isPassed());
	}

	/**
	 * Gets the "children" of this test result that passed
	 * 
	 * @return the children of this test result, if any, or an empty collection
	 */
	@Override
	public Collection<? extends TestResult> getPassedTests() {
		return singletonListOrEmpty(isPassed());
	}

	/**
	 * Gets the "children" of this test result that were skipped
	 * 
	 * @return the children of this test result, if any, or an empty list
	 */
	@Override
	public Collection<? extends TestResult> getSkippedTests() {
		return singletonListOrEmpty(isSkipped());
	}

	private Collection<? extends hudson.tasks.test.TestResult> singletonListOrEmpty(boolean f) {
		if(f)
			return Collections.singletonList(this);
		else
			return emptyList();
	}

	/**
	 * If there was an error or a failure, this is the stack trace, or otherwise null.
	 */
	@Override
	@Exported
	public String getErrorStackTrace() {
		String result = null;
		try {
			JUnitTestDetailInfo junitTestDetailInfo = junitDB.readTestDetail(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName(), null, null);
			result = junitTestDetailInfo.getErrorStackTrace();
		}
		catch(SQLException sE) {
			LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
		}
		catch(IOException iE) {
			LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
		}
		return result;
	}

	/**
	 * If there was an error or a failure, this is the text from the message.
	 */
	@Override
	@Exported
	public String getErrorDetails() {
		String result = null;
		try {
			JUnitTestDetailInfo junitTestDetailInfo = junitDB.readTestDetail(testInfo.getBuildNumber(), testInfo.getProjectName(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName(), null, null);
			result = junitTestDetailInfo.getErrorMessage();
		}
		catch(SQLException sE) {
			LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
		}
		catch(IOException iE) {
			LOGGER.log(Level.SEVERE, iE.getMessage(), iE);
		}
		return result;
	}

	/**
	 * @return true if the test was not skipped and did not fail, false otherwise.
	 */
	@Override
	public boolean isPassed() {
		return testInfo.getStatus() == JUnitTestInfo.STATUS_SUCCESS;
	}

	/**
	 * Tests whether the test was skipped or not. TestNG allows tests to be skipped if their dependencies fail or they are part of a group that has
	 * been configured to be skipped.
	 * 
	 * @return true if the test was not executed, false otherwise.
	 */
	@Exported(visibility = 9)
	public boolean isSkipped() {
		return testInfo.getStatus() == JUnitTestInfo.STATUS_SKIP;
	}

	@Override
	public AbstractBuild<?, ?> getOwner() {
		return(parent == null ? null : parent.getOwner());
	}

	public int compareTo(CaseResult that) {
		return this.getFullName().compareTo(that.getFullName());
	}

	@Exported(name = "status", visibility = 9)
	// because stapler notices suffix 's' and remove it
	public Status getStatus() {
		if(isSkipped()) {
			return Status.SKIPPED;
		}
		JUnitTestInfo junitTestInfo = getPreviousTestInfo();
		if(junitTestInfo == null) {
			return isPassed() ? Status.PASSED : Status.FAILED;
		}

		if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SUCCESS) {
			return isPassed() ? Status.PASSED : Status.REGRESSION;
		}
		else {
			return isPassed() ? Status.FIXED : Status.FAILED;
		}
	}

	private JUnitTestInfo getPreviousTestInfo() {
		if(previousTestInfo == null) {
			try {
				previousTestInfo = junitDB.queryTestCaseForBuildPriorTo(testInfo.getProjectName(), testInfo.getBuildNumber(), testInfo.getSuiteName(), testInfo.getPackageName(), testInfo.getClassName(), testInfo.getCaseName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return previousTestInfo;
	}

	/**
	 * Constants that represent the status of this test.
	 */
	public enum Status {
		/**
		 * This test runs OK, just like its previous run.
		 */
		PASSED("result-passed", Messages._CaseResult_Status_Passed(), true),
		/**
		 * This test was skipped due to configuration or the failure or skipping of a method that it depends on.
		 */
		SKIPPED("result-skipped", Messages._CaseResult_Status_Skipped(), false),
		/**
		 * This test failed, just like its previous run.
		 */
		FAILED("result-failed", Messages._CaseResult_Status_Failed(), false),
		/**
		 * This test has been failing, but now it runs OK.
		 */
		FIXED("result-fixed", Messages._CaseResult_Status_Fixed(), true),
		/**
		 * This test has been running OK, but now it failed.
		 */
		REGRESSION("result-regression", Messages._CaseResult_Status_Regression(), false);

		private final String cssClass;
		private final Localizable message;
		public final boolean isOK;

		Status(String cssClass, Localizable message, boolean OK) {
			this.cssClass = cssClass;
			this.message = message;
			isOK = OK;
		}

		public String getCssClass() {
			return cssClass;
		}

		public String getMessage() {
			return message.toString();
		}

		public boolean isRegression() {
			return this == REGRESSION;
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

	/**
	 * For sorting errors by age.
	 */
	/*package*/static final Comparator<CaseResult> BY_AGE = new Comparator<CaseResult>() {
		public int compare(CaseResult lhs, CaseResult rhs) {
			return lhs.getAge() - rhs.getAge();
		}
	};

	private static final long serialVersionUID = 1L;
}
