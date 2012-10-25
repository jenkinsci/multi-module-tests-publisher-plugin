package com.cwctravel.hudson.plugins.multimoduletests.junit;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.tasks.junit.TestAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;

import java.lang.ref.WeakReference;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public class ProjectResult extends MetaTabulatedResult {
	private static final long serialVersionUID = -6091389434656908226L;

	private static final Logger LOGGER = Logger.getLogger(ProjectResult.class.getName());

	private int failedSince;

	private String description = "";

	private ProjectResultBuildAction parentAction;

	private List<String> moduleNames;

	private JUnitDB junitDB;

	private JUnitSummaryInfo summary;
	private JUnitSummaryInfo previousSummary;

	private JUnitMetricsInfo metrics;

	private transient WeakReference<History> historyReference;
	private transient Map<String, ModuleResult> moduleResultMap;
	private transient Set<String> emptyModuleResults;

	/**
	 * Allow the object to rebuild its internal data structures when it is deserialized.
	 */
	private Object readResolve() {
		return this;
	}

	public ProjectResult() {}

	public ProjectResult(AbstractProject<?, ?> project, JUnitSummaryInfo summary, String moduleNamesStr, String description) {
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

	public ProjectResult(AbstractBuild<?, ?> build, JUnitSummaryInfo summary, String moduleNamesStr, String description) {
		this(build.getProject(), summary, moduleNamesStr, description);
	}

	/**
	 * The list of moduleNames currently in use by the children
	 * 
	 * @return
	 */
	public Collection<String> getModuleNames() {
		List<String> result = new ArrayList<String>();
		for(ModuleResult testResult: getChildren()) {
			result.add(testResult.getName());
		}
		return result;
	}

	@Exported(inline = true, visibility = 99)
	public Collection<ModuleResult> getModules() {
		return getChildren();
	}

	public ModuleResult getModuleResultByName(String moduleName) {
		ModuleResult result = getModuleResultFromCache(moduleName);
		if(result == null) {
			try {
				JUnitSummaryInfo junitSummaryInfo = junitDB.fetchTestModuleSummaryForBuildNoLaterThan(summary.getBuildNumber(), summary.getProjectName(), moduleName);

				if(junitSummaryInfo != null) {
					result = new ModuleResult(this, junitSummaryInfo);
					cacheModuleResult(moduleName, result);
				}
			}
			catch(SQLException sE) {
				throw new JUnitException(sE);
			}
		}
		return result;
	}

	private boolean isModuleResultEmpty(String moduleName) {
		if(emptyModuleResults != null && emptyModuleResults.contains(moduleName)) {
			return true;
		}
		return false;
	}

	private ModuleResult getModuleResultFromCache(String moduleName) {
		if(moduleResultMap != null) {
			return moduleResultMap.get(moduleName);
		}
		return null;
	}

	private void cacheModuleResult(String moduleName, ModuleResult testResult) {
		if(moduleResultMap == null) {
			moduleResultMap = new WeakValueHashMap<String, ModuleResult>();
		}
		moduleResultMap.put(moduleName, testResult);
	}

	@Override
	public ProjectResultBuildAction getTestResultAction() {
		if(parentAction == null) {
			LOGGER.finest("null parentAction");
		}
		return parentAction;
	}

	@Override
	public List<TestAction> getTestActions() {
		return ProjectResultBuildAction.getTestActions(this, getTestResultAction());
	}

	public void setParentAction(ProjectResultBuildAction parentAction) {
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
	public ProjectResult getPreviousResult() {
		if(parentAction != null) {
			AbstractBuild<?, ?> b = parentAction.owner;
			while(true) {
				b = b.getPreviousBuild();
				if(b == null)
					return null;
				ProjectResultBuildAction r = b.getAction(ProjectResultBuildAction.class);
				if(r != null)
					return r.getResultAsProjectResult();
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
				previousSummary = junitDB.fetchTestProjectSummaryForBuildPriorTo(summary.getBuildNumber(), summary.getProjectName());
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
		if(action instanceof ProjectResultBuildAction) {
			return ((ProjectResultBuildAction)action).getResultAsProjectResult();
		}

		return (ModuleResult)action.getResult();
	}

	@Override
	public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
		String moduleName;
		String remainingId = null;
		int moduleNameEnd = id.indexOf('/');
		if(moduleNameEnd < 0) {
			moduleName = id;
			remainingId = null;
		}
		else {
			moduleName = id.substring(0, moduleNameEnd);
			if(moduleNameEnd != id.length()) {
				remainingId = id.substring(moduleNameEnd + 1);
				if(remainingId.length() == 0) {
					remainingId = null;
				}
			}
		}
		ModuleResult group = getModuleResultByName(moduleName);
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
					ModuleResult testResult = new ModuleResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_MODULE, junitDB, junitTestInfo));
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
					ModuleResult testResult = new ModuleResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_MODULE, junitDB, junitTestInfo));
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
					ModuleResult testResult = new ModuleResult(this, new LazyJUnitSummaryInfo(LazyJUnitSummaryInfo.SUMMARY_TYPE_MODULE, junitDB, junitTestInfo));
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
	public Collection<ModuleResult> getChildren() {
		AbstractBuild<?, ?> build = getOwner();

		List<ModuleResult> result = new ArrayList<ModuleResult>();
		try {
			for(String moduleName: moduleNames) {
				ModuleResult testResult = getModuleResultFromCache(moduleName);
				if(testResult == null && !isModuleResultEmpty(moduleName)) {
					JUnitSummaryInfo junitSummaryInfo = junitDB.fetchTestModuleSummaryForBuildNoLaterThan(build.getNumber(), build.getProject().getName(), moduleName);

					if(junitSummaryInfo != null) {
						testResult = new ModuleResult(this, junitSummaryInfo);
						cacheModuleResult(moduleName, testResult);
					}
					else {
						addEmptyModuleResult(moduleName);
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

	private void addEmptyModuleResult(String moduleName) {
		if(emptyModuleResults == null) {
			emptyModuleResults = new HashSet<String>();
		}
		emptyModuleResults.add(moduleName);

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
		// If there's a test with that moduleName, serve up that test.
		ModuleResult thatOne = getModuleResultByName(token);
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
		Collection<String> moduleNames = getModuleNames();
		for(String moduleName: moduleNames) {
			sb.append(moduleName);
		}
		return sb.toString();
	}

	@Override
	public hudson.tasks.test.TestResult getTopLevelTestResult() {
		return this;
	}

	@Override
	public hudson.tasks.junit.History getHistory() {
		History history = null;
		if(historyReference == null || (history = historyReference.get()) == null) {
			history = new History(this, 5000);
			historyReference = new WeakReference<History>(history);
		}
		return history;
	}

	public String getRootUrl(String urlName) {
		return getTestResultAction().getRootUrl(this, urlName);
	}

	public String getRootUrl(TestAction testAction) {
		return getTestResultAction().getRootUrl(this, testAction);
	}
}
