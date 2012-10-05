package com.cwctravel.hudson.plugins.suitegroupedtests;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.test.TestResultAggregator;
import hudson.tasks.test.TestResultParser;
import hudson.util.DescribableList;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.cwctravel.hudson.plugins.suitegroupedtests.SuiteGroupResultAction.Data;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.CaseResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.ClassResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.PackageResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.SuiteGroupResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.TestResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitTestInfo;

public class SuiteGroupResultPublisher extends Recorder implements Serializable, MatrixAggregatable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4830492397041441842L;
	private static final Logger LOGGER = Logger.getLogger(SuiteGroupResultPublisher.class.getName());
	private static List<TestResultParser> testResultParsers = null;

	private SuiteTestGroupConfiguration config;

	private DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers;

	@DataBoundConstructor
	public SuiteGroupResultPublisher(SuiteTestGroupConfiguration config) {
		if(config == null) {
			throw new IllegalArgumentException("Null or empty list of configs passed in to SuiteTestResultGroupPublisher. Please file a bug.");
		}
		this.config = config;
	}

	public DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> getTestDataPublishers() {
		return testDataPublishers;
	}

	public void setTestDataPublishers(DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers) {
		this.testDataPublishers = testDataPublishers;
	}

	/**
	 * Declares the scope of the synchronization monitor this {@link hudson.tasks.BuildStep} expects from outside.
	 */
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}

	public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
		return new TestResultAggregator(build, launcher, listener);
	}

	public void debugPrint() {
		LOGGER.info("got config: " + config.toString());

		for(TestResultParser parser: testResultParsers) {
			LOGGER.info("we have test result parser: " + parser.getClass().getName());
		}
	}

	public SuiteTestGroupConfiguration getConfig() {
		return config;
	}

	public void setConfig(SuiteTestGroupConfiguration config) {
		this.config = config;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		String startMsg = "Analyzing test results with SuiteTestResultGroupPublisher...";
		listener.getLogger().println(startMsg);
		LOGGER.fine(startMsg);

		// Prepare to process results
		final long buildTime = build.getTimestamp().getTimeInMillis();
		final long nowMaster = System.currentTimeMillis();

		SuiteGroupResult suiteGroupResult = build.getWorkspace().act(new ParseResultCallable(config.getTestResultFileMask(), buildTime, nowMaster, config.isKeepLongStdio()));

		SuiteGroupResultAction action = new SuiteGroupResultAction(build, suiteGroupResult, listener);
		build.addAction(action);
		suiteGroupResult.setParentAction(action);
		suiteGroupResult.freeze(action);
		suiteGroupResult.tally();

		List<Data> data = new ArrayList<Data>();
		if(testDataPublishers != null) {
			for(TestDataPublisher tdp: testDataPublishers) {
				Data d = tdp.getTestData(build, launcher, listener, suiteGroupResult);
				if(d != null) {
					data.add(d);
				}
			}
		}
		action.setData(data);

		try {
			final JUnitDB junitDB = new JUnitDB(build.getProject().getRootDir().getAbsolutePath());
			List<TestResult> testResults = new ArrayList<TestResult>(suiteGroupResult.getChildren());

			List<JUnitTestInfo> junitTests = new ArrayList<JUnitTestInfo>();

			for(TestResult testResult: testResults) {
				List<PackageResult> packageResults = new ArrayList<PackageResult>(testResult.getChildren());
				for(PackageResult packageResult: packageResults) {
					List<ClassResult> classResults = new ArrayList<ClassResult>(packageResult.getChildren());
					for(ClassResult classResult: classResults) {
						List<CaseResult> caseResults = new ArrayList<CaseResult>(classResult.getChildren());
						for(CaseResult caseResult: caseResults) {
							JUnitTestInfo junitTestInfo = new JUnitTestInfo();
							junitTestInfo.setBuildId(build.getId());
							junitTestInfo.setBuildNumber(build.getNumber());
							junitTestInfo.setProjectName(build.getProject().getName());
							junitTestInfo.setSuiteName(testResult.getName());
							junitTestInfo.setStartTime(build.getTimeInMillis());
							junitTestInfo.setPackageName(packageResult.getName());
							junitTestInfo.setClassName(classResult.getName());
							junitTestInfo.setCaseName(caseResult.getName());
							junitTestInfo.setDuration((long)(caseResult.getDuration() * 1000));

							CaseResult.Status caseStatus = caseResult.getStatus();
							if(caseStatus == CaseResult.Status.PASSED) {
								junitTestInfo.setStatus(JUnitTestInfo.STATUS_SUCCESS);
							}
							else if(caseStatus == CaseResult.Status.FAILED || caseStatus == CaseResult.Status.REGRESSION) {
								junitTestInfo.setStatus(JUnitTestInfo.STATUS_FAIL);
							}
							else if(caseStatus == CaseResult.Status.SKIPPED) {
								junitTestInfo.setStatus(JUnitTestInfo.STATUS_SKIP);
							}
							junitTests.add(junitTestInfo);
							if(junitTests.size() > 1000) {
								junitDB.insertTests(junitTests);
								junitTests.clear();
							}
						}

					}
				}

			}

			if(junitTests.size() > 0) {
				junitDB.insertTests(junitTests);
			}
		}
		catch(SQLException e) {
			LOGGER.log(Level.SEVERE, "Could not persist results to database: " + e.getMessage(), e);
		}

		Result healthResult = determineBuildHealth(build, suiteGroupResult);

		// Parsers can only decide to make the build worse than it currently is, never better.
		if(healthResult != null && healthResult.isWorseThan(build.getResult())) {
			build.setResult(healthResult);
		}

		String debugString = suiteGroupResult.toString(); // resultGroup.toPrettyString();
		LOGGER.info("Test results parsed: " + debugString);
		listener.getLogger().println("Test results parsed: " + debugString);

		return true;
	}

	private Result determineBuildHealth(AbstractBuild<?, ?> build, SuiteGroupResult suiteGroupResult) {
		// Set build health on the basis of all configured test report groups
		Result worstSoFar = build.getResult();

		for(hudson.tasks.test.TestResult result: suiteGroupResult.getChildren()) {
			Result thisResult = result.getBuildResult();
			if(thisResult != null && thisResult.isWorseThan(worstSoFar)) {
				worstSoFar = result.getBuildResult();
			}
		}

		return worstSoFar;
	}

	@Override
	public Action getProjectAction(AbstractProject<?, ?> project) {
		return new SuiteGroupResultProjectAction(project);
	}

	public DescriptorExtensionList<TestDataPublisher, Descriptor<TestDataPublisher>> getTestDataPublisherDescriptors() {
		return TestDataPublisher.all();
	}

	private static final class ParseResultCallable implements FilePath.FileCallable<SuiteGroupResult> {
		private static final long serialVersionUID = -2412534164383439939L;

		private final long buildTime;
		private final String testResults;
		private final long nowMaster;
		private final boolean keepLongStdio;

		private ParseResultCallable(String testResults, long buildTime, long nowMaster, boolean keepLongStdio) {
			this.buildTime = buildTime;
			this.testResults = testResults;
			this.nowMaster = nowMaster;
			this.keepLongStdio = keepLongStdio;
		}

		public SuiteGroupResult invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
			final long nowSlave = System.currentTimeMillis();

			FileSet fs = Util.createFileSet(ws, testResults);
			DirectoryScanner ds = fs.getDirectoryScanner();

			SuiteGroupResult suiteGroupResult = new SuiteGroupResult("(no description)", buildTime + (nowSlave - nowMaster), ds, keepLongStdio);

			return suiteGroupResult;
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public String getDisplayName() {
			return "Publish JUnit test result report grouped by suite";
		}

		@Override
		public Publisher newInstance(StaplerRequest req, JSONObject formData) throws hudson.model.Descriptor.FormException {
			LOGGER.info(formData.toString());
			SuiteGroupResultPublisher publisher = req.bindJSON(SuiteGroupResultPublisher.class, formData);

			DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers = new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(Saveable.NOOP);
			try {
				testDataPublishers.rebuild(req, formData.getJSONObject("config"), TestDataPublisher.all());
			}
			catch(IOException e) {
				throw new FormException(e, null);
			}

			publisher.setTestDataPublishers(testDataPublishers);

			return publisher;
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			// for Maven we have SurefireArchiver that automatically kicks in.
			return !"AbstractMavenProject".equals(jobType.getClass().getSimpleName());
		}
	}
}
