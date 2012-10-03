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
import hudson.tasks.junit.History;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.MetaTabulatedResult;
import hudson.tasks.test.TestObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import com.cwctravel.hudson.plugins.suitegroupedtests.SuiteGroupResultAction;

/**
 * Root of all the test results for one build.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class TestResult extends MetaTabulatedResult {
	private static final Logger LOGGER = Logger.getLogger(TestResult.class.getName());

	/**
	 * List of all {@link SuiteResult}s in this test. This is the core data structure to be persisted in the disk.
	 */
	private final List<SuiteResult> suites = new ArrayList<SuiteResult>();

	/**
	 * {@link #suites} keyed by their names for faster lookup.
	 */
	private transient Map<String, SuiteResult> suitesByName;

	/**
	 * Results tabulated by package.
	 */
	private transient Map<String, PackageResult> byPackages;

	// set during the freeze phase
	private transient AbstractTestResultAction<?> parentAction;

	private transient TestObject parent;

	/**
	 * Number of all tests.
	 */
	private transient int totalTests;

	private transient int skippedTests;

	private float duration;

	/**
	 * Number of failed/error tests.
	 */
	private transient List<CaseResult> failedTests;

	private String suiteName;

	/**
	 * Creates an empty result.
	 */
	public TestResult() {}

	public TestResult(TestObject parent, String suiteName, SuiteResult suiteResult) {
		this.parent = parent;
		this.suiteName = suiteName;
		add(suiteResult);
	}

	public TestResult(TestObject parent, String suiteName, List<SuiteResult> suiteResults) {
		this.parent = parent;
		this.suiteName = suiteName;

		if(suiteResults != null) {
			for(SuiteResult suiteResult: suiteResults) {
				add(suiteResult);
			}
		}
	}

	@Override
	public TestObject getParent() {
		return parent;
	}

	@Override
	public void setParent(TestObject parent) {
		this.parent = parent;
	}

	private void add(SuiteResult sr) {
		for(SuiteResult s: suites) {
			// a common problem is that people parse TEST-*.xml as well as TESTS-TestSuite.xml
			// see http://www.nabble.com/Problem-with-duplicate-build-execution-td17549182.html for discussion
			if(s.getName().equals(sr.getName()) && eq(s.getTimestamp(), sr.getTimestamp()))
				return; // duplicate
		}
		suites.add(sr);
		duration += sr.getDuration();
	}

	private boolean eq(Object lhs, Object rhs) {
		return lhs != null && rhs != null && lhs.equals(rhs);
	}

	public String getDisplayName() {
		return suiteName;
	}

	@Override
	public AbstractBuild<?, ?> getOwner() {
		return(parentAction == null ? null : parentAction.owner);
	}

	@Override
	public hudson.tasks.test.TestResult findCorrespondingResult(String id) {
		if(getId().equals(id) || (id == null)) {
			return this;
		}

		String firstElement = null;
		String subId = null;
		int sepIndex = id.indexOf('/');
		if(sepIndex < 0) {
			firstElement = id;
			subId = null;
		}
		else {
			firstElement = id.substring(0, sepIndex);
			subId = id.substring(sepIndex + 1);
			if(subId.length() == 0) {
				subId = null;
			}
		}

		String packageName = null;
		if(firstElement.equals(getId())) {
			sepIndex = subId.indexOf('/');
			if(sepIndex < 0) {
				packageName = subId;
				subId = null;
			}
			else {
				packageName = subId.substring(0, sepIndex);
				subId = subId.substring(sepIndex + 1);
			}
		}
		else {
			packageName = firstElement;
			subId = null;
		}
		PackageResult child = byPackage(packageName);
		if(child != null) {
			if(subId != null) {
				return child.findCorrespondingResult(subId);
			}
			else {
				return child;
			}
		}
		else {
			return null;
		}
	}

	@Override
	public String getTitle() {
		return Messages.TestResult_getTitle(getName());
	}

	@Override
	public String getChildTitle() {
		return Messages.TestResult_getChildTitle();
	}

	@Exported(visibility = 999)
	@Override
	public float getDuration() {
		return duration;
	}

	@Exported(visibility = 999)
	@Override
	public int getPassCount() {
		return totalTests - getFailCount() - getSkipCount();
	}

	@Exported(visibility = 999)
	@Override
	public int getFailCount() {
		if(failedTests == null)
			return 0;
		else
			return failedTests.size();
	}

	@Exported(visibility = 999)
	@Override
	public int getSkipCount() {
		return skippedTests;
	}

	public int getPassDiff() {
		TestResult prev = (TestResult)getPreviousResult();
		if(prev == null)
			return getPassCount();
		return getPassCount() - prev.getPassCount();
	}

	public int getSkipDiff() {
		TestResult prev = (TestResult)getPreviousResult();
		if(prev == null)
			return getSkipCount();
		return getSkipCount() - prev.getSkipCount();
	}

	public int getFailDiff() {
		TestResult prev = (TestResult)getPreviousResult();
		if(prev == null)
			return getFailCount();
		return getFailCount() - prev.getFailCount();
	}

	public int getTotalDiff() {
		TestResult prev = (TestResult)getPreviousResult();
		if(prev == null)
			return getTotalCount();
		return getTotalCount() - prev.getTotalCount();
	}

	@Override
	public List<CaseResult> getFailedTests() {
		return failedTests;
	}

	/**
	 * Gets the "children" of this test result that passed
	 * 
	 * @return the children of this test result, if any, or an empty collection
	 */
	@Override
	public Collection<? extends hudson.tasks.test.TestResult> getPassedTests() {
		throw new UnsupportedOperationException(); // TODO: implement!(FIXME: generated)
	}

	/**
	 * Gets the "children" of this test result that were skipped
	 * 
	 * @return the children of this test result, if any, or an empty list
	 */
	@Override
	public Collection<? extends hudson.tasks.test.TestResult> getSkippedTests() {
		throw new UnsupportedOperationException(); // TODO: implement!(FIXME: generated)
	}

	/**
	 * If this test failed, then return the build number when this test started failing.
	 */
	@Override
	public int getFailedSince() {
		throw new UnsupportedOperationException(); // TODO: implement!(FIXME: generated)
	}

	/**
	 * If this test failed, then return the run when this test started failing.
	 */
	@Override
	public Run<?, ?> getFailedSinceRun() {
		throw new UnsupportedOperationException(); // TODO: implement!(FIXME: generated)
	}

	/**
	 * The stdout of this test.
	 * <p/>
	 * <p/>
	 * Depending on the tool that produced the XML report, this method works somewhat inconsistently. With some tools (such as Maven surefire plugin),
	 * you get the accurate information, that is the stdout from this test case. With some other tools (such as the JUnit task in Ant), this method
	 * returns the stdout produced by the entire test suite.
	 * <p/>
	 * <p/>
	 * If you need to know which is the case, compare this output from {@link SuiteResult#getStdout()}.
	 * 
	 * @since 1.294
	 */
	@Override
	public String getStdout() {
		StringBuilder sb = new StringBuilder();
		for(SuiteResult suite: suites) {
			sb.append("Standard Out (stdout) for Suite: " + suite.getName());
			sb.append(suite.getStdout());
		}
		return sb.toString();
	}

	/**
	 * The stderr of this test.
	 * 
	 * @see #getStdout()
	 * @since 1.294
	 */
	@Override
	public String getStderr() {
		StringBuilder sb = new StringBuilder();
		for(SuiteResult suite: suites) {
			sb.append("Standard Error (stderr) for Suite: " + suite.getName());
			sb.append(suite.getStderr());
		}
		return sb.toString();
	}

	/**
	 * If there was an error or a failure, this is the stack trace, or otherwise null.
	 */
	@Override
	public String getErrorStackTrace() {
		return "No error stack traces available at this level. Drill down to individual tests to find stack traces.";
	}

	/**
	 * If there was an error or a failure, this is the text from the message.
	 */
	@Override
	public String getErrorDetails() {
		return "No error details available at this level. Drill down to individual tests to find details.";
	}

	/**
	 * @return true if the test was not skipped and did not fail, false otherwise.
	 */
	@Override
	public boolean isPassed() {
		return(getFailCount() == 0);
	}

	@Override
	public Collection<PackageResult> getChildren() {
		return byPackages.values();
	}

	/**
	 * Whether this test result has children.
	 */
	@Override
	public boolean hasChildren() {
		return !suites.isEmpty();
	}

	@Exported(inline = true, visibility = 9)
	public Collection<SuiteResult> getSuites() {
		return suites;
	}

	@Override
	public String getName() {
		return suiteName;
	}

	@Override
	public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
		if(token.equals(getId())) {
			return this;
		}

		PackageResult result = byPackage(token);
		if(result != null) {
			return result;
		}
		else {
			return super.getDynamic(token, req, rsp);
		}
	}

	public PackageResult byPackage(String packageName) {
		return byPackages.get(packageName);
	}

	public SuiteResult getSuite(String name) {
		return suitesByName.get(name);
	}

	@Override
	public void setParentAction(AbstractTestResultAction action) {
		this.parentAction = action;
		tally(); // I want to be sure to inform our children when we get an action.
	}

	@Override
	public AbstractTestResultAction getParentAction() {
		return this.parentAction;
	}

	/**
	 * Recount my children.
	 */
	@Override
	public void tally() {
		// / Empty out data structures
		// TODO: free children? memmory leak?
		suitesByName = new HashMap<String, SuiteResult>();
		failedTests = new ArrayList<CaseResult>();
		byPackages = new TreeMap<String, PackageResult>();

		totalTests = 0;
		skippedTests = 0;

		// Ask all of our children to tally themselves
		for(SuiteResult s: suites) {
			s.setParent(this); // kluge to prevent double-counting the results
			suitesByName.put(s.getName(), s);
			List<CaseResult> cases = s.getCases();

			for(CaseResult cr: cases) {
				cr.setParentAction(this.parentAction);
				cr.setParentSuiteResult(s);
				cr.tally();
				String pkg = cr.getPackageName(), spkg = safe(pkg);
				PackageResult pr = byPackage(spkg);
				if(pr == null)
					byPackages.put(spkg, pr = new PackageResult(this, pkg));
				pr.add(cr);
			}
		}

		for(PackageResult pr: byPackages.values()) {
			pr.tally();
			skippedTests += pr.getSkipCount();
			failedTests.addAll(pr.getFailedTests());
			totalTests += pr.getTotalCount();
		}
	}

	/**
	 * Builds up the transient part of the data structure from results {@link #parse(File) parsed} so far.
	 * <p>
	 * After the data is frozen, more files can be parsed and then freeze can be called again.
	 */
	public void freeze(SuiteGroupResultAction parent) {
		this.parentAction = parent;
		if(suitesByName == null) {
			// freeze for the first time
			suitesByName = new HashMap<String, SuiteResult>();
			totalTests = 0;
			failedTests = new ArrayList<CaseResult>();
			byPackages = new TreeMap<String, PackageResult>();
		}

		for(SuiteResult s: suites) {
			if(!s.freeze(this)) // this is disturbing: has-a-parent is conflated with has-been-counted
				continue;

			suitesByName.put(s.getName(), s);

			totalTests += s.getCases().size();
			for(CaseResult cr: s.getCases()) {
				if(cr.isSkipped())
					skippedTests++;
				else if(!cr.isPassed())
					failedTests.add(cr);

				String pkg = cr.getPackageName(), spkg = safe(pkg);
				PackageResult pr = byPackage(spkg);
				if(pr == null)
					byPackages.put(spkg, pr = new PackageResult(this, pkg));
				pr.add(cr);
			}
		}

		Collections.sort(failedTests, CaseResult.BY_AGE);

		for(PackageResult pr: byPackages.values())
			pr.freeze();
	}

	@Override
	public History getHistory() {
		return new com.cwctravel.hudson.plugins.suitegroupedtests.junit.History(this, 5000);
	}

	private static final long serialVersionUID = 1L;
}
