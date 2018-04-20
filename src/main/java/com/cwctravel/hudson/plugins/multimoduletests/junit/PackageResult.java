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
package com.cwctravel.hudson.plugins.multimoduletests.junit;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.junit.TestAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import com.cwctravel.hudson.plugins.multimoduletests.ProjectResultBuildAction;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitMetricsInfo;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitSummaryInfo;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitTestInfo;

/**
 * Cumulative test result for a package.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class PackageResult extends MetaTabulatedResult implements Comparable<PackageResult> {

	private static final Logger LOGGER = Logger.getLogger(PackageResult.class.getName());

	private int failedSince;

	private final JUnitDB junitDB;
	private final TestObject parent;

	private transient WeakReference<History> historyReference;
	private transient WeakReference<List<ClassResult>> childrenReference;

	private final JUnitSummaryInfo summary;
	private JUnitSummaryInfo previousSummary;

	private JUnitMetricsInfo metrics;

	PackageResult(TestObject parent, JUnitSummaryInfo summary) {
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
	public ProjectResultBuildAction getTestResultAction() {
		return (ProjectResultBuildAction)super.getTestResultAction();
	}

	@Override
	public List<TestAction> getTestActions() {
		return ProjectResultBuildAction.getTestActions(this, getTestResultAction());
	}

	@Override
	@Exported(visibility = 999)
	public String getName() {
		return summary.getPackageName();
	}

	@Override
	public String getSafeName() {
		return safe(getName());
	}

	@Override
	public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
		String myID = safe(getName());
		int base = id.indexOf(myID);
		String className;
		String subId = null;
		if(base > 0) {
			int classNameStart = base + myID.length() + 1;
			className = id.substring(classNameStart);
		}
		else {
			className = id;
		}
		int classNameEnd = className.indexOf('/');
		if(classNameEnd > 0) {
			subId = className.substring(classNameEnd + 1);
			if(subId.length() == 0) {
				subId = null;
			}
			className = className.substring(0, classNameEnd);
		}

		ClassResult child = getClassResult(className);
		if(child != null) {
			if(subId != null) {
				return child.findCorrespondingResult(subId);
			}
			else {
				return child;
			}
		}

		return null;
	}

	@Override
	public String getTitle() {
		return Messages.PackageResult_getTitle(getParent().getDisplayName(), getName());
	}

	@Override
	public String getChildTitle() {
		return Messages.PackageResult_getChildTitle();
	}

	// TODO: wait until stapler 1.60 to do this @Exported
	@Override
	public float getDuration() {
		return (float)summary.getDuration() / 1000;
	}

	@Exported
	@Override
	public int getPassCount() {
		return (int)summary.getPassCount();
	}

	@Exported
	@Override
	public int getFailCount() {
		return (int)(summary.getFailCount() + summary.getErrorCount());
	}

	@Exported
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

	public JUnitSummaryInfo getSummary() {
		return summary;
	}

	public JUnitMetricsInfo getMetrics() {
		if(metrics == null) {
			try {
				metrics = junitDB.fetchTestPackageMetrics(summary.getBuildNumber(), summary.getProjectName(), summary.getModuleName(), summary.getPackageName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return metrics;
	}

	@Override
	public PackageResult getPreviousResult() {
		return new PackageResult(getParent(), getPreviousSummary());
	}

	private JUnitSummaryInfo getPreviousSummary() {
		if(previousSummary == null) {
			try {
				previousSummary = junitDB.fetchTestPackageSummaryForBuildPriorTo(summary.getBuildNumber(), summary.getProjectName(), summary.getModuleName(), summary.getPackageName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return previousSummary;
	}

	@Override
	public Object getDynamic(String name, StaplerRequest req, StaplerResponse rsp) {
		ClassResult result = getClassResult(name);
		if(result != null) {
			return result;
		}
		else {
			return super.getDynamic(name, req, rsp);
		}
	}

	public ClassResult getClassResult(String name) {
		Collection<ClassResult> children = getChildren();
		for(ClassResult classResult: children) {
			if(name.equals(classResult.getName())) {
				return classResult;
			}
		}
		return null;
	}

	private List<ClassResult> getCachedChildren() {
		if(childrenReference != null) {
			return childrenReference.get();
		}
		return null;
	}

	private void cacheChildren(List<ClassResult> children) {
		childrenReference = new WeakReference<List<ClassResult>>(children);
	}

	@Override
	@Exported(name = "child")
	public Collection<ClassResult> getChildren() {
		List<ClassResult> result = getCachedChildren();
		if(result == null) {
			try {
				result = new ArrayList<ClassResult>();
				List<JUnitSummaryInfo> junitSummaryInfoList = junitDB.fetchTestPackageChildrenForBuild(summary.getBuildNumber(), summary.getProjectName(), summary.getModuleName(), summary.getPackageName());
				for(JUnitSummaryInfo junitSummaryInfo: junitSummaryInfoList) {
					ClassResult classResult = new ClassResult(this, junitSummaryInfo);
					result.add(classResult);
				}
				cacheChildren(result);
			}
			catch(SQLException sE) {
				throw new JUnitException(sE);
			}
		}
		return result;
	}

	/**
	 * Whether this test result has children.
	 */
	@Override
	public boolean hasChildren() {
		return summary.getTotalCount() > 0;
	}

	@Override
	public List<CaseResult> getFailedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByPackage(summary.getProjectName(), summary.getBuildNumber(), summary.getModuleName(), summary.getPackageName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_FAIL || junitTestInfo.getStatus() == JUnitTestInfo.STATUS_ERROR) {
					ClassResult classResult = new ClassResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_CLASS, junitDB, junitTestInfo));
					CaseResult caseResult = new CaseResult(classResult, junitTestInfo);
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
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByPackage(summary.getProjectName(), summary.getBuildNumber(), summary.getModuleName(), summary.getPackageName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SKIP) {
					ClassResult classResult = new ClassResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_CLASS, junitDB, junitTestInfo));
					CaseResult caseResult = new CaseResult(classResult, junitTestInfo);
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
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByPackage(summary.getProjectName(), summary.getBuildNumber(), summary.getModuleName(), summary.getPackageName());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SUCCESS) {
					ClassResult classResult = new ClassResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_CLASS, junitDB, junitTestInfo));
					CaseResult caseResult = new CaseResult(classResult, junitTestInfo);
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
	 * Returns a list of the failed cases, sorted by age.
	 * 
	 * @return
	 */
	public List<CaseResult> getFailedTestsSortedByAge() {
		List<CaseResult> failedTests = getFailedTests();
		Collections.sort(failedTests, CaseResult.BY_AGE);
		return failedTests;
	}

	@Override
	public int getFailedSince() {
		if(failedSince == 0 && getFailCount() > 0) {
			try {
				List<JUnitSummaryInfo> history = junitDB.summarizeTestPackageHistory(summary.getProjectName(), summary.getModuleName(), summary.getPackageName(), 5000);
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

	/**
	 * @return true if every test was not skipped and every test did not fail, false otherwise.
	 */
	@Override
	public boolean isPassed() {
		return summary.getFailCount() == 0 && summary.getErrorCount() == 0 && summary.getSkipCount() == 0;
	}

	public int compareTo(PackageResult that) {
		return summary.getPackageName().compareTo(that.getSummary().getPackageName());
	}

	public String getDisplayName() {
		return summary.getPackageName();
	}

	public String getRootUrl(String urlName) {
		return getTestResultAction().getRootUrl(this, urlName);
	}

	public String getRootUrl(TestAction testAction) {
		return getTestResultAction().getRootUrl(this, testAction);
	}

	@Override
	public hudson.tasks.junit.History getHistory() {
		History history = null;
		if(historyReference == null || (history = historyReference.get()) == null) {
			history = new History(this, 5000);
			historyReference = new WeakReference<History>(history);
			return history;
		}
		return history;
	}
}
