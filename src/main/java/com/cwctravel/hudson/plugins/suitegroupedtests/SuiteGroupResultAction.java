package com.cwctravel.hudson.plugins.suitegroupedtests;

import hudson.XmlFile;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestObject;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerProxy;

import com.cwctravel.hudson.plugins.suitegroupedtests.junit.CaseResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.SuiteGroupResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.SuiteResult;
import com.cwctravel.hudson.plugins.suitegroupedtests.junit.TestResult;
import com.thoughtworks.xstream.XStream;

public class SuiteGroupResultAction extends AbstractTestResultAction<SuiteGroupResultAction> implements StaplerProxy {
	static public final String RESULT_DATA_FILENAME = "testResultGroups.xml";
	private int failCount;
	private int skipCount;
	private Integer totalCount; // TODO: can we make this just a normal int, and find another way to check
	// whether we're populated yet? (This technique is borrowed from hudson core TestResultAction.)

	/**
	 * Store the result group itself in a separate file so we don't eat up too much memory.
	 */
	private transient WeakReference<SuiteGroupResult> resultGroupReference;

	public SuiteGroupResultAction(AbstractBuild owner, SuiteGroupResult r, BuildListener listener) {
		super(owner);
		setResult(r, listener);
	}

	/**
	 * Store the data to a separate file, and update our cached values.
	 */
	public synchronized void setResult(SuiteGroupResult r, BuildListener listener) {

		r.setParentAction(this);

		totalCount = r.getTotalCount();
		failCount = r.getFailCount();
		skipCount = r.getSkipCount();

		// persist the data
		try {
			getDataFile().write(r);
		}
		catch(IOException e) {
			e.printStackTrace(listener.fatalError("Failed to save the labeled test groups publisher's test result"));
		}

		this.resultGroupReference = new WeakReference<SuiteGroupResult>(r);
	}

	private XmlFile getDataFile() {
		return new XmlFile(XSTREAM, new File(owner.getRootDir(), RESULT_DATA_FILENAME));
	}

	public Object getTarget() {
		return getResult();
	}

	/**
	 * Gets the number of failed tests.
	 */
	@Override
	public int getFailCount() {
		if(totalCount == null)
			getResult(); // this will load the result from disk if necessary
		return failCount;
	}

	/**
	 * Gets the total number of skipped tests
	 * 
	 * @return
	 */
	@Override
	public int getSkipCount() {
		if(totalCount == null)
			getResult(); // this will load the result from disk if necessary
		return skipCount;
	}

	/**
	 * Gets the total number of tests.
	 */
	@Override
	public int getTotalCount() {
		if(totalCount == null)
			getResult(); // this will load the result from disk if necessary
		return totalCount;
	}

	/**
	 * Get the result that this action represents. If necessary, the result will be loaded from disk.
	 * 
	 * @return
	 */
	@Override
	public synchronized SuiteGroupResult getResult() {
		SuiteGroupResult r;
		if(resultGroupReference == null) {
			r = load();
			resultGroupReference = new WeakReference<SuiteGroupResult>(r);
		}
		else {
			r = resultGroupReference.get();
		}

		if(r == null) {
			r = load();
			resultGroupReference = new WeakReference<SuiteGroupResult>(r);
		}
		if(r == null) {
			logger.severe("Couldn't get result for SuiteGroupResult " + this);
			return null;
		}

		if(totalCount == null) {
			totalCount = r.getTotalCount();
			failCount = r.getFailCount();
			skipCount = r.getSkipCount();
		}
		return r;
	}

	/**
	 * Loads a {@link MetaLabeledTestResultGroup} from disk.
	 */
	private SuiteGroupResult load() {
		SuiteGroupResult r;
		try {
			r = (SuiteGroupResult)getDataFile().read();
		}
		catch(IOException e) {
			logger.log(Level.WARNING, "Failed to load " + getDataFile(), e);
			r = new SuiteGroupResult(); // return a dummy
		}
		r.setParentAction(this);
		return r;
	}

	/**
	 * This convenience method is what getResult() should have been, but with a specified return type.
	 * 
	 * @return
	 */
	public SuiteGroupResult getResultAsSuiteGroupResult() {
		return getResult();
	}

	public TestResult getSuiteTestResultGroup(String suiteName) {
		return getResult().getGroupBySuiteName(suiteName);
	}

	@Override
	public String getDescription(TestObject testObject) {
		return getResult().getDescription();
	}

	@Override
	public void setDescription(TestObject testObject, String s) {
		getResult().setDescription(s);
	}

	@Override
	public String getDisplayName() {
		return "Test Results";
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

	private static final Logger logger = Logger.getLogger(SuiteGroupResultAction.class.getName());

	private static final XStream XSTREAM = new XStream2();

	static {
		XSTREAM.alias("suite-group", SuiteGroupResult.class);
		XSTREAM.alias("result", TestResult.class);
		XSTREAM.alias("suite", SuiteResult.class);
		XSTREAM.alias("case", CaseResult.class);
	}
}
