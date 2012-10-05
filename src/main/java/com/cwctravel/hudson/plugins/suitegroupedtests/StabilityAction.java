package com.cwctravel.hudson.plugins.suitegroupedtests;

import hudson.tasks.junit.TestAction;
import hudson.tasks.test.TestObject;

import org.kohsuke.stapler.StaplerProxy;

import com.cwctravel.hudson.plugins.suitegroupedtests.junit.Stability;

public class StabilityAction extends TestAction implements StaplerProxy {
	private final TestObject testObject;

	public StabilityAction(TestObject testObject) {
		this.testObject = testObject;
	}

	public String getIconFileName() {
		return "graph.png";
	}

	public String getDisplayName() {
		return "Test Stability";
	}

	public String getUrlName() {
		return "testStability";
	}

	public Object getTarget() {
		return new Stability(testObject);
	}

}
