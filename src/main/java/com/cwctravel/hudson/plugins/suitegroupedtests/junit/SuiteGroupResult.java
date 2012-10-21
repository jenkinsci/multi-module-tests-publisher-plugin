package com.cwctravel.hudson.plugins.suitegroupedtests.junit;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.History;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public class SuiteGroupResult extends MetaTabulatedResult {
	private static final long serialVersionUID = -6091389434656908226L;

	private static final Logger LOGGER = Logger.getLogger(SuiteGroupResult.class.getName());

	private int failedSince;

	private String description = "";

	private SuiteGroupResultAction parentAction;

	private List<String> moduleNames;

	private JUnitDB junitDB;

	private JUnitSummaryInfo summary;
	private JUnitSummaryInfo previousSummary;

	private JUnitMetricsInfo metrics;

	private transient Map<String, TestResult> testResultMap;
	private transient Set<String> emptyTestResults;

	/**
	 * Allow the object to rebuild its internal data structures when it is deserialized.
	 */
	private Object readResolve() {
		return this;
	}

	public SuiteGroupResult() {}

	public SuiteGroupResult(AbstractProject<?, ?> project, JUnitSummaryInfo summary, String moduleNamesStr, String description) {
		this.description = description;
		try {
			this.junitDB = new JUnitDB(project.getRootDir().getAbsolutePath());
			this.summary = summary;
			moduleNames = new ArrayList<String>();
			if(moduleNamesStr != null) {
				String[] moduleNamesArray = moduleNamesStr.split(",");
				for(String moduleName: moduleNamesArray) {
					moduleNames.add(moduleName);
				}
			}
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
	}

	public SuiteGroupResult(AbstractBuild<?, ?> build, JUnitSummaryInfo summary, String moduleNamesStr, String description) {
		this(build.getProject(), summary, moduleNamesStr, description);
	}

	/**
	 * The list of suiteNames currently in use by the children
	 * 
	 * @return
	 */
	public Collection<String> getSuiteNames() {
		List<String> result = new ArrayList<String>();
		for(TestResult testResult: getChildren()) {
			result.add(testResult.getName());
		}
		return result;
	}

	@Exported(inline = true, visibility = 99)
	public Collection<TestResult> getGroups() {
		return getChildren();
	}

	public TestResult getGroupBySuiteName(String suiteName) {
		TestResult result = getTestResultFromCache(suiteName);
		if(result == null) {
			try {
				JUnitSummaryInfo junitSummaryInfo = junitDB.summarizeTestSuiteForBuildNoLaterThan(summary.getBuildNumber(), summary.getProjectName(), suiteName);

				if(junitSummaryInfo != null) {
					result = new TestResult(this, junitSummaryInfo);
					cacheTestResult(suiteName, result);
				}
			}
			catch(SQLException sE) {
				throw new JUnitException(sE);
			}
		}
		return result;
	}

	private boolean isTestResultEmpty(String suiteName) {
		if(emptyTestResults != null && emptyTestResults.contains(suiteName)) {
			return true;
		}
		return false;
	}

	private TestResult getTestResultFromCache(String suiteName) {
		if(testResultMap != null) {
			return testResultMap.get(suiteName);
		}
		return null;
	}

	private void cacheTestResult(String suiteName, TestResult testResult) {
		if(testResultMap == null) {
			testResultMap = new HashMap<String, TestResult>();
		}
		testResultMap.put(suiteName, testResult);
	}

	@Override
	public SuiteGroupResultAction getTestResultAction() {
		if(parentAction == null) {
			LOGGER.finest("null parentAction");
		}
		return parentAction;
	}

	@Override
	public List<TestAction> getTestActions() {
		return SuiteGroupResultAction.getTestActions(this, getTestResultAction());
	}

	public void setParentAction(SuiteGroupResultAction parentAction) {
		if(this.parentAction != parentAction) {
			this.parentAction = parentAction;
		}
	}

	@Override
	public String getTitle() {
		return "Test Report";
	}

	@Override
	public String getName() {
		return "";
	}

	@Override
	public boolean isPassed() {
		return summary.getFailCount() == 0 && summary.getErrorCount() == 0 && summary.getSkipCount() == 0;
	}

	@Override
	public String getChildTitle() {
		return "Group";
	}

	@Override
	public SuiteGroupResult getPreviousResult() {
		if(parentAction != null) {
			AbstractBuild<?, ?> b = parentAction.owner;
			while(true) {
				b = b.getPreviousBuild();
				if(b == null)
					return null;
				SuiteGroupResultAction r = b.getAction(SuiteGroupResultAction.class);
				if(r != null)
					return r.getResultAsSuiteGroupResult();
			}
		}
		return null;
	}

	public JUnitMetricsInfo getMetrics() {
		if(metrics == null) {
			try {
				metrics = junitDB.fetchTestProjectMetrics(summary.getBuildNumber(), summary.getProjectName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return metrics;
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

	public JUnitSummaryInfo getPreviousSummary() {
		if(previousSummary == null) {
			try {
				previousSummary = junitDB.summarizeTestProjectForBuildPriorTo(summary.getBuildNumber(), summary.getProjectName());
			}
			catch(SQLException sE) {
				LOGGER.log(Level.SEVERE, sE.getMessage(), sE);
			}
		}
		return previousSummary;
	}

	@Override
	public hudson.tasks.test.TestResult getResultInBuild(AbstractBuild<?, ?> build) {
		AbstractTestResultAction<?> action = build.getAction(AbstractTestResultAction.class);
		if(action == null) {
			return null;
		}
		if(action instanceof SuiteGroupResultAction) {
			return ((SuiteGroupResultAction)action).getResultAsSuiteGroupResult();
		}

		return (TestResult)action.getResult();
	}

	@Override
	public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
		String groupName;
		String remainingId = null;
		int groupNameEnd = id.indexOf('/');
		if(groupNameEnd < 0) {
			groupName = id;
			remainingId = null;
		}
		else {
			groupName = id.substring(0, groupNameEnd);
			if(groupNameEnd != id.length()) {
				remainingId = id.substring(groupNameEnd + 1);
				if(remainingId.length() == 0) {
					remainingId = null;
				}
			}
		}
		TestResult group = getGroupBySuiteName(groupName);
		if(group != null) {
			if(remainingId != null) {
				return group.findCorrespondingResult(remainingId);
			}
			else {
				return group;
			}
		}

		return null;
	}

	@Override
	public int getFailedSince() { // TODO: implement this.
		// If we haven't calculated failedSince yet, and we should,
		// do it now.
		if(failedSince == 0 && getFailCount() > 0) {
			try {
				List<JUnitSummaryInfo> history = junitDB.summarizeTestProjectHistory(summary.getProjectName(), 5000);
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
	 * Gets the number of failed tests.
	 */
	@Exported(visibility = 99)
	@Override
	public int getFailCount() {
		return (int)(summary.getFailCount() + summary.getErrorCount());
	}

	/**
	 * Gets the total number of skipped tests
	 * 
	 * @return
	 */
	@Override
	@Exported(visibility = 99)
	public int getSkipCount() {
		return (int)summary.getSkipCount();
	}

	/**
	 * Gets the number of passed tests.
	 */
	@Exported(visibility = 99)
	@Override
	public int getPassCount() {
		return (int)summary.getPassCount();
	}

	@Override
	public List<CaseResult> getFailedTests() {
		try {
			List<CaseResult> result = new ArrayList<CaseResult>();
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByProject(summary.getProjectName(), summary.getBuildId());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_FAIL || junitTestInfo.getStatus() == JUnitTestInfo.STATUS_ERROR) {
					TestResult testResult = new TestResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_SUITE, junitDB, junitTestInfo));
					PackageResult packageResult = new PackageResult(testResult, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_PACKAGE, junitDB, junitTestInfo));
					ClassResult classResult = new ClassResult(packageResult, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_CLASS, junitDB, junitTestInfo));
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
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByProject(summary.getProjectName(), summary.getBuildId());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SKIP) {
					TestResult testResult = new TestResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_SUITE, junitDB, junitTestInfo));
					PackageResult packageResult = new PackageResult(testResult, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_PACKAGE, junitDB, junitTestInfo));
					ClassResult classResult = new ClassResult(packageResult, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_CLASS, junitDB, junitTestInfo));
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
			List<JUnitTestInfo> junitTestInfoList = junitDB.queryTestsByProject(summary.getProjectName(), summary.getBuildId());
			for(JUnitTestInfo junitTestInfo: junitTestInfoList) {
				if(junitTestInfo.getStatus() == JUnitTestInfo.STATUS_SUCCESS) {
					TestResult testResult = new TestResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_SUITE, junitDB, junitTestInfo));
					PackageResult packageResult = new PackageResult(testResult, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_PACKAGE, junitDB, junitTestInfo));
					ClassResult classResult = new ClassResult(packageResult, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_CLASS, junitDB, junitTestInfo));
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
	public Collection<TestResult> getChildren() {
		AbstractBuild<?, ?> build = getOwner();
		List<TestResult> result = new ArrayList<TestResult>();
		try {
			for(String moduleName: moduleNames) {
				TestResult testResult = getTestResultFromCache(moduleName);
				if(testResult == null && !isTestResultEmpty(moduleName)) {
					JUnitSummaryInfo junitSummaryInfo = junitDB.summarizeTestSuiteForBuildNoLaterThan(build.getNumber(), build.getProject().getName(), moduleName);

					if(junitSummaryInfo != null) {
						testResult = new TestResult(this, junitSummaryInfo);
						cacheTestResult(moduleName, testResult);
					}
					else {
						addEmptyTestResult(moduleName);
					}
				}

				if(testResult != null) {
					result.add(testResult);
				}
			}
		}
		catch(SQLException sE) {
			throw new JUnitException(sE);
		}
		return result;
	}

	private void addEmptyTestResult(String suiteName) {
		if(emptyTestResults == null) {
			emptyTestResults = new HashSet<String>();
		}
		emptyTestResults.add(suiteName);

	}

	@Override
	public boolean hasChildren() {
		return(summary.getTotalCount() != 0);
	}

	@Override
	public AbstractBuild<?, ?> getOwner() {
		if(parentAction != null)
			return parentAction.owner;
		else
			return null;
	}

	/**
	 * Strange API. A LabeledTestResultGroup is always the direct child of an action, so we'll just return null here, for the parent. This is the same
	 * behavior as TestResult.
	 * 
	 * @return
	 */
	@Override
	public TestObject getParent() {
		return null;
	}

	@Exported(visibility = 99)
	@Override
	public float getDuration() {
		return summary.getDuration() / 1000;
	}

	@Exported(visibility = 99)
	/* @Override */
	public String getDisplayName() {
		return "Modules" + "(#" + summary.getBuildNumber() + ")";
	}

	@Exported(visibility = 99)
	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
		// If there's a test with that suiteName, serve up that test.
		TestResult thatOne = getGroupBySuiteName(token);
		if(thatOne != null) {
			return thatOne;
		}
		else {
			return super.getDynamic(token, req, rsp);
		}
	}

	@Override
	public String toPrettyString() {
		StringBuilder sb = new StringBuilder();
		Collection<String> suiteNames = getSuiteNames();
		for(String suiteName: suiteNames) {
			sb.append(suiteName);
		}
		return sb.toString();
	}

	@Override
	public hudson.tasks.test.TestResult getTopLevelTestResult() {
		return this;
	}

	@Override
	public History getHistory() {
		return new com.cwctravel.hudson.plugins.suitegroupedtests.junit.History(this, 5000);
	}

	public String getRootUrl(String urlName) {
		return getTestResultAction().getRootUrl(this, urlName);
	}

	public String getRootUrl(TestAction testAction) {
		return getTestResultAction().getRootUrl(this, testAction);
	}
}
