package com.cwctravel.hudson.plugins.suitegroupedtests.junit;

import hudson.AbortException;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.History;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;
import hudson.util.IOException2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.tools.ant.DirectoryScanner;
import org.dom4j.DocumentException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import com.cwctravel.hudson.plugins.suitegroupedtests.SuiteGroupResultAction;

public class SuiteGroupResult extends MetaTabulatedResult {
	private static final long serialVersionUID = -6091389434656908226L;

	private static final boolean checkTimestamps = true; // TODO: change to System.getProperty

	private static final List<TestAction> EMPTY_TEST_ACTIONS_LIST = new ArrayList<TestAction>();

	protected final Map<String, TestResult> childrenBySuiteName;

	protected transient Map<String, Collection<TestResult>> failedTestsBySuiteName;
	protected transient Map<String, Collection<TestResult>> passedTestsBySuiteName;
	protected transient Map<String, Collection<TestResult>> skippedTestsBySuiteName;
	protected transient Collection<TestResult> allFailedTests;
	protected transient Collection<TestResult> allPassedTests;
	protected transient Collection<TestResult> allSkippedTests;

	protected final boolean keepLongStdio;

	protected int failCount = 0;
	protected int skipCount = 0;
	protected int passCount = 0;
	protected int totalCount = 0;

	protected float duration = 0;

	protected transient boolean cacheDirty = true;
	protected transient SuiteGroupResultAction parentAction = null;

	protected String description = "";

	/** Effectively overrides TestObject.id, by overriding the accessors */
	protected String groupId = "";

	private static final Logger LOGGER = Logger.getLogger(SuiteGroupResult.class.getName());

	/**
	 * Allow the object to rebuild its internal data structures when it is deserialized.
	 */
	private Object readResolve() {
		failedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		passedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		skippedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		allPassedTests = new HashSet<TestResult>();
		allFailedTests = new HashSet<TestResult>();
		allSkippedTests = new HashSet<TestResult>();
		updateCache();
		return this;
	}

	public SuiteGroupResult() {
		childrenBySuiteName = new HashMap<String, TestResult>(10);
		failedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		passedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		skippedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		allPassedTests = new HashSet<TestResult>();
		allFailedTests = new HashSet<TestResult>();
		allSkippedTests = new HashSet<TestResult>();
		this.keepLongStdio = false;
		cacheDirty = true;
	}

	public SuiteGroupResult(String description, long buildTime, DirectoryScanner ds, boolean keepLongStdio) throws IOException {
		childrenBySuiteName = new HashMap<String, TestResult>(10);
		failedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		passedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		skippedTestsBySuiteName = new HashMap<String, Collection<TestResult>>(10);
		allPassedTests = new HashSet<TestResult>();
		allFailedTests = new HashSet<TestResult>();
		allSkippedTests = new HashSet<TestResult>();

		this.description = description;
		this.keepLongStdio = keepLongStdio;

		if(ds != null) {
			parse(buildTime, ds);
		}

		cacheDirty = true;
	}

	@Override
	public void tally() {
		updateCache();
	}

	/**
	 * The list of suiteNames currently in use by the children
	 * 
	 * @return
	 */
	public Collection<String> getSuiteNames() {
		if(cacheDirty)
			updateCache();
		return childrenBySuiteName.keySet();
	}

	@Exported(inline = true, visibility = 99)
	public Collection<TestResult> getGroups() {
		if(cacheDirty)
			updateCache();
		return childrenBySuiteName.values();
	}

	public TestResult getGroupBySuiteName(String suiteName) {
		if(cacheDirty)
			updateCache();

		TestResult group = childrenBySuiteName.get(suiteName);
		return group;
	}

	@Override
	public SuiteGroupResultAction getTestResultAction() {
		if(parentAction == null) {
			LOGGER.finest("null parentAction");
		}
		return parentAction;
	}

	/**
	 * I wish the superclass didn't call this. FIXME. TODO.
	 * 
	 * @return
	 */
	@Override
	public List<TestAction> getTestActions() {
		return EMPTY_TEST_ACTIONS_LIST;
	}

	public void setParentAction(SuiteGroupResultAction parentAction) {
		if(this.parentAction == parentAction) {
			return;
		}

		this.parentAction = parentAction;
		// Tell all of our children about the parent action, too.
		for(TestResult group: childrenBySuiteName.values()) {
			group.setParentAction(parentAction);
		}
	}

	public void addTestResult(String suiteName, TestResult result) {
		childrenBySuiteName.put(suiteName, result);
		cacheDirty = true;
	}

	@Override
	public String getTitle() {
		return "Test Reports";
	}

	@Override
	public String getName() {
		return "";
	}

	@Override
	public boolean isPassed() {
		if(cacheDirty)
			updateCache();
		return (failCount == 0) && (skipCount == 0);
	}

	@Override
	public String getChildTitle() {
		return "Group";
	}

	@Override
	public SuiteGroupResult getPreviousResult() {
		if(parentAction == null)
			return null;
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

	public int getPassDiff() {
		SuiteGroupResult prev = getPreviousResult();
		if(prev == null)
			return getPassCount();
		return getPassCount() - prev.getPassCount();
	}

	public int getSkipDiff() {
		SuiteGroupResult prev = getPreviousResult();
		if(prev == null)
			return getSkipCount();
		return getSkipCount() - prev.getSkipCount();
	}

	public int getFailDiff() {
		SuiteGroupResult prev = getPreviousResult();
		if(prev == null)
			return getFailCount();
		return getFailCount() - prev.getFailCount();
	}

	public int getTotalDiff() {
		SuiteGroupResult prev = getPreviousResult();
		if(prev == null)
			return getTotalCount();
		return getTotalCount() - prev.getTotalCount();
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
		throw new UnsupportedOperationException(" Not yet implemented."); // TODO:
																			// implement
	}

	@Override
	public Run<?, ?> getFailedSinceRun() { // TODO: implement this.
		throw new UnsupportedOperationException(" Not yet implemented."); // TODO:
																			// implement
	}

	/**
	 * Gets the number of failed tests.
	 */
	@Exported(visibility = 99)
	@Override
	public int getFailCount() {
		if(cacheDirty)
			updateCache();
		return failCount;
	}

	/**
	 * Gets the total number of skipped tests
	 * 
	 * @return
	 */
	@Override
	@Exported(visibility = 99)
	public int getSkipCount() {
		if(cacheDirty)
			updateCache();
		return skipCount;
	}

	/**
	 * Gets the number of passed tests.
	 */
	@Exported(visibility = 99)
	@Override
	public int getPassCount() {
		if(cacheDirty)
			updateCache();
		return passCount;
	}

	@Override
	public Collection<? extends TestResult> getFailedTests() {
		// BAD result to force problems -- this method is now effectively UNIMPLEMENTED
		LOGGER.severe("getFailedTests unimplemented. Expect garbage.");
		if(cacheDirty)
			updateCache();
		return allFailedTests;
	}

	@Override
	public Collection<? extends TestResult> getSkippedTests() {
		LOGGER.severe("getSkippedTests unimplemented. Expect garbage.");
		if(cacheDirty)
			updateCache();
		return allSkippedTests;
	}

	@Override
	public Collection<? extends TestResult> getPassedTests() {
		LOGGER.severe("getSkippedTests unimplemented. Expect garbage.");
		if(cacheDirty)
			updateCache();
		return allPassedTests;
	}

	@Override
	public Collection<? extends TestResult> getChildren() {
		if(cacheDirty)
			updateCache();
		return childrenBySuiteName.values();
	}

	@Override
	public boolean hasChildren() {
		if(cacheDirty)
			updateCache();
		return(totalCount != 0);
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
		if(cacheDirty)
			updateCache();
		return duration;
	}

	@Exported(visibility = 99)
	/* @Override */
	public String getDisplayName() {
		return "Test Result Groups";
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
		if(cacheDirty)
			updateCache();

		// If there's a test with that suiteName, serve up that test.
		TestResult thatOne = childrenBySuiteName.get(token);
		if(thatOne != null) {
			return thatOne;
		}
		else {
			return super.getDynamic(token, req, rsp);
		}
	}

	@Override
	public String toPrettyString() {
		if(cacheDirty)
			updateCache();
		StringBuilder sb = new StringBuilder();
		Set<String> suiteNames = childrenBySuiteName.keySet();
		for(String suiteName: suiteNames) {
			sb.append(suiteName);
		}
		return sb.toString();
	}

	private void storeInCache(String suiteName, Map<String, Collection<TestResult>> sameStatusCollection, TestResult r) {
		if(sameStatusCollection.keySet().contains(suiteName)) {
			sameStatusCollection.get(suiteName).add(r);
		}
		else {
			List<TestResult> newCollection = new ArrayList<TestResult>(Arrays.asList(r));
			sameStatusCollection.put(suiteName, newCollection);
		}
	}

	private void updateCache() {
		failedTestsBySuiteName.clear();
		skippedTestsBySuiteName.clear();
		passedTestsBySuiteName.clear();
		allFailedTests.clear();
		allPassedTests.clear();
		allSkippedTests.clear();
		passCount = 0;
		failCount = 0;
		skipCount = 0;
		float durationAccum = 0.0f;

		Collection<String> suiteNames = childrenBySuiteName.keySet();
		for(String l: suiteNames) {
			TestResult testResult = childrenBySuiteName.get(l);
			testResult.setParentAction(parentAction);
			testResult.tally();
			passCount += testResult.getPassCount();
			failCount += testResult.getFailCount();
			skipCount += testResult.getSkipCount();

			durationAccum += testResult.getDuration();
			if(testResult.isPassed()) {
				storeInCache(l, passedTestsBySuiteName, testResult);
				allPassedTests.add(testResult);
			}
			else if(testResult.getFailCount() > 0) {
				storeInCache(l, failedTestsBySuiteName, testResult);
				allFailedTests.add(testResult);
			}
			else {
				storeInCache(l, skippedTestsBySuiteName, testResult);
				allSkippedTests.add(testResult);
			}
		}

		duration = durationAccum;
		totalCount = passCount + failCount + skipCount;

		cacheDirty = false;
	}

	/**
	 * Collect reports from the given {@link DirectoryScanner}, while filtering out all files that were created before the given time.
	 */
	public void parse(long buildTime, DirectoryScanner results) throws IOException {
		String[] includedFiles = results.getIncludedFiles();
		File baseDir = results.getBasedir();

		boolean parsed = false;

		for(String value: includedFiles) {
			File reportFile = new File(baseDir, value);
			// only count files that were actually updated during this build
			if((buildTime - 3000/*error margin*/<= reportFile.lastModified()) || !checkTimestamps) {
				if(reportFile.length() == 0) {
					// this is a typical problem when JVM quits abnormally, like OutOfMemoryError during a test.
					SuiteResult sr = new SuiteResult(reportFile.getName(), "", "");
					sr.addCase(new CaseResult(sr, "<init>", "Test report file " + reportFile.getAbsolutePath() + " was length 0"));
					TestResult testResult = new TestResult(this, "(unknown)", sr);
					childrenBySuiteName.put(testResult.getName(), testResult);
				}
				else {
					parse(reportFile);
				}
				parsed = true;
			}
		}

		if(!parsed) {
			long localTime = System.currentTimeMillis();
			if(localTime < buildTime - 1000) /*margin*/
				// build time is in the the future. clock on this slave must be running behind
				throw new AbortException("Clock on this slave is out of sync with the master, and therefore \n" + "I can't figure out what test results are new and what are old.\n" + "Please keep the slave clock in sync with the master.");

			File f = new File(baseDir, includedFiles[0]);
			throw new AbortException(String.format("Test reports were found but none of them are new. Did tests run? \n" + "For example, %s is %s old\n", f, Util.getTimeSpanString(buildTime - f.lastModified())));
		}
	}

	public void parse(File reportFile) throws IOException {
		try {
			Map<String, List<SuiteResult>> groupedSuiteResultsMap = new HashMap<String, List<SuiteResult>>();

			for(SuiteResult suiteResult: SuiteResult.parse(reportFile, keepLongStdio)) {
				String suiteName = suiteResult.getName();
				List<SuiteResult> groupedSuiteResults = groupedSuiteResultsMap.get(suiteName);
				if(groupedSuiteResults == null) {
					groupedSuiteResults = new ArrayList<SuiteResult>();
					groupedSuiteResultsMap.put(suiteName, groupedSuiteResults);
				}
				groupedSuiteResults.add(suiteResult);
			}

			for(Map.Entry<String, List<SuiteResult>> groupedSuiteResultsEntry: groupedSuiteResultsMap.entrySet()) {
				String suiteName = groupedSuiteResultsEntry.getKey();
				List<SuiteResult> groupedSuiteResults = groupedSuiteResultsEntry.getValue();
				TestResult testResult = new TestResult(this, suiteName, groupedSuiteResults);
				childrenBySuiteName.put(suiteName, testResult);
			}

		}
		catch(RuntimeException e) {
			throw new IOException2("Failed to read " + reportFile, e);
		}
		catch(DocumentException e) {
			if(!reportFile.getPath().endsWith(".xml")) {
				throw new IOException2("Failed to read " + reportFile + "\n" + "Is this really a JUnit report file? Your configuration must be matching too many files", e);
			}
			else {
				throw new IOException2("Failed to read " + reportFile, e);
			}
		}
	}

	public void freeze(SuiteGroupResultAction action) {
		for(TestResult testResult: childrenBySuiteName.values()) {
			testResult.freeze(action);
		}
	}

	@Override
	public hudson.tasks.test.TestResult getTopLevelTestResult() {
		return this;
	}

	@Override
	public History getHistory() {
		return new com.cwctravel.hudson.plugins.suitegroupedtests.junit.History(this, 5000);
	}
}
