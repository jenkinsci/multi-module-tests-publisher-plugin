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
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;

import net.sf.json.JSONObject;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.xml.sax.SAXException;

import com.cwctravel.hudson.plugins.suitegroupedtests.SuiteGroupResultAction.Data;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.JUnitParser;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.SuiteGroupResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitDB;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.db.JUnitSummaryInfo;

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

		File junitDBDir = build.getProject().getRootDir();
		JUnitSummaryInfo summary = build.getWorkspace().act(new ParseResultCallable(junitDBDir, build.getId(), build.getNumber(), build.getProject().getName(), config.getTestResultFileMask(), buildTime, nowMaster, config.isKeepLongStdio()));

		if(summary != null) {
			SuiteGroupResult suiteGroupResult = new SuiteGroupResult(build, summary, "(no description)");
			SuiteGroupResultAction action = new SuiteGroupResultAction(build, suiteGroupResult, listener);
			build.addAction(action);

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

			Result healthResult = determineBuildHealth(build, suiteGroupResult);

			// Parsers can only decide to make the build worse than it currently is, never better.
			if(healthResult != null && healthResult.isWorseThan(build.getResult())) {
				build.setResult(healthResult);
			}
		}
		LOGGER.info("Test results parsed.");
		listener.getLogger().println("Test results parsed.");

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

	private static final class ParseResultCallable implements FilePath.FileCallable<JUnitSummaryInfo> {
		private static final long serialVersionUID = -2412534164383439939L;
		private static final boolean checkTimestamps = true; // TODO: change to System.getProperty

		private final boolean keepLongStdio;

		private final int buildNumber;

		private final long buildTime;
		private final long nowMaster;

		private final String buildId;
		private final String projectName;
		private final String testResults;

		private final File junitDBDir;

		private ParseResultCallable(File junitDBDir, String buildId, int buildNumber, String projectName, String testResults, long buildTime,
				long nowMaster, boolean keepLongStdio) {
			this.buildId = buildId;
			this.buildNumber = buildNumber;
			this.projectName = projectName;
			this.buildTime = buildTime;
			this.testResults = testResults;
			this.nowMaster = nowMaster;

			this.keepLongStdio = keepLongStdio;

			this.junitDBDir = junitDBDir;
		}

		public JUnitSummaryInfo invoke(File ws, VirtualChannel channel) throws IOException, InterruptedException {
			final long nowSlave = System.currentTimeMillis();
			JUnitSummaryInfo summary = null;
			FileSet fs = Util.createFileSet(ws, testResults);
			DirectoryScanner ds = fs.getDirectoryScanner();
			String[] includedFiles = ds.getIncludedFiles();
			File baseDir = ds.getBasedir();

			try {
				JUnitDB junitDB = new JUnitDB(junitDBDir.getAbsolutePath());
				JUnitParser junitParser = new JUnitParser(junitDB);
				for(String value: includedFiles) {
					File reportFile = new File(baseDir, value);
					// only count files that were actually updated during this build
					if((buildTime + (nowSlave - nowMaster) - 3000/*error margin*/<= reportFile.lastModified()) || !checkTimestamps) {
						if(reportFile.length() != 0) {
							junitParser.parse(buildNumber, buildId, projectName, reportFile);
						}
					}
				}
				summary = junitDB.summarizeTestProjectForBuild(buildNumber, projectName);
			}
			catch(SAXException sAE) {
				throw new IOException(sAE);
			}
			catch(ParserConfigurationException pCE) {
				throw new IOException(pCE);
			}
			catch(SQLException sE) {
				throw new IOException(sE);
			}

			return null;
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
