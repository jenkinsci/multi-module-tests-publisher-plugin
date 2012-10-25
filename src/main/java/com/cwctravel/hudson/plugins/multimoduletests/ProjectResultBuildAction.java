package com.cwctravel.hudson.plugins.multimoduletests;

import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.junit.TestAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestObject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;

import com.cwctravel.hudson.plugins.multimoduletests.junit.ModuleResult;
import com.cwctravel.hudson.plugins.multimoduletests.junit.ProjectResult;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.multimoduletests.junit.db.JUnitSummaryInfo;

public class ProjectResultBuildAction extends AbstractTestResultAction<ProjectResultBuildAction> implements StaplerProxy {
	private static final Logger LOGGER = Logger.getLogger(ProjectResultBuildAction.class.getName());

	public static abstract class Data {
		/**
		 * Returns all TestActions for the testObject.
		 * 
		 * @return Can be empty but never null. The caller must assume that the returned list is read-only.
		 */
		public abstract List<? extends TestAction> getTestAction(TestObject testObject);
	}

	private List<Data> testData = new ArrayList<Data>();

	/**
	 * Store the result group itself in a separate file so we don't eat up too much memory.
	 */
	private transient JUnitSummaryInfo summary;
	private final String moduleNames;
	private transient ProjectResult projectResult;

	public ProjectResultBuildAction(AbstractBuild<?, ?> owner, String moduleNames, BuildListener listener) {
		super(owner);
		this.moduleNames = moduleNames;
	}

	public Object getTarget() {
		return getResult();
	}

	/**
	 * Gets the number of failed tests.
	 */
	@Override
	public int getFailCount() {
		return (int)(getSummary().getFailCount() + getSummary().getErrorCount());
	}

	/**
	 * Gets the total number of skipped tests
	 * 
	 * @return
	 */
	@Override
	public int getSkipCount() {
		return (int)getSummary().getSkipCount();
	}

	/**
	 * Gets the total number of tests.
	 */
	@Override
	public int getTotalCount() {
		return (int)getSummary().getTotalCount();
	}

	public List<TestAction> getActions(TestObject object) {
		List<TestAction> result = new ArrayList<TestAction>();
		// Added check for null testData to avoid NPE from issue 4257.
		if(testData != null) {
			for(Data data: testData) {
				result.addAll(data.getTestAction(object));
			}
		}
		return Collections.unmodifiableList(result);

	}

	public void setData(List<Data> testData) {
		this.testData = testData;
	}

	private JUnitSummaryInfo getSummary() {
		if(summary == null) {
			JUnitDB junitDB;
			try {
				junitDB = new JUnitDB(owner.getProject().getRootDir().getAbsolutePath());
				summary = junitDB.fetchTestProjectSummaryForBuildNoLaterThan(owner.getNumber(), owner.getProject().getName());
			}
			catch(SQLException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}

		}
		return summary;
	}

	/**
	 * Get the result that this action represents. If necessary, the result will be loaded from disk.
	 * 
	 * @return
	 */
	@Override
	public ProjectResult getResult() {
		if(projectResult == null) {
			projectResult = new ProjectResult(owner, getSummary(), moduleNames, "(no description)");
			projectResult.setParentAction(this);
		}
		return projectResult;
	}

	/**
	 * This convenience method is what getResult() should have been, but with a specified return type.
	 * 
	 * @return
	 */
	public ProjectResult getResultAsProjectResult() {
		return getResult();
	}

	public ModuleResult getModuleResult(String moduleName) {
		return getResult().getModuleResultByName(moduleName);
	}

	@Override
	public void setDescription(TestObject testObject, String s) {
		getResult().setDescription(s);
	}

	@Override
	public String getDisplayName() {
		return "Test Results";
	}

	public String getRootUrl(TestObject testObject, TestAction testAction) {
		return getRootUrl(testObject, testAction.getUrlName());
	}

	public String getRootUrl(TestObject testObject, String urlName) {
		String buildUrl = testObject.getOwner().getUrl();
		String testObjectUrl = testObject.getUrl();
		String result = Stapler.getCurrentRequest().getContextPath() + (buildUrl.startsWith("/") ? "" : "/") + buildUrl + getUrlName() + (testObjectUrl.startsWith("/") ? "" : "/") + testObjectUrl + (testObjectUrl.endsWith("/") ? "" : "/") + urlName;
		return result;
	}

	public String getModuleNames() {
		return moduleNames;
	}

	/**
	 * Bring this object into an internally-consistent state after deserializing it. For a MetaLabeledTestResultGroupAction , we don't have to do
	 * anything, because the WeakReference handles loading the actual test result data from disk when it is requested. The only case where we have
	 * something to do here is if this object was serialized with the test result data inline, rather than in a separate file. If the data was inline,
	 * we do a little dance to move the data into a separate file.
	 * 
	 * @return
	 */
	@Override
	public Object readResolve() {
		// This method is called when an instance of this object is loaded from
		// persistent storage into memory. We use this opportunity to detect
		// and convert from storing the test results in the same file as the
		// build.xml to storing the test results in a separate file.
		return this;
	}

	public static List<TestAction> getTestActions(TestObject testObject, AbstractTestResultAction<?> testResultAction) {
		if(testResultAction != null && testResultAction instanceof ProjectResultBuildAction) {
			ProjectResultBuildAction sgra = (ProjectResultBuildAction)testResultAction;
			return sgra.getActions(testObject);
		}
		else {
			return new ArrayList<TestAction>();
		}
	}
}
