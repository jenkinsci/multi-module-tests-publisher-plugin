package com.cwctravel.hudson.plugins.suitegroupedtests.junit;

import hudson.Functions;
import hudson.model.AbstractModelObject;
import hudson.tasks.junit.TestAction;
import hudson.tasks.test.TestObject;

public class Stability extends AbstractModelObject {
	private final TestObject parent;

	public Stability(TestObject parent) {
		this.parent = parent;
	}

	public TestObject getParent() {
		return parent;
	}

	public String getRootUrl(TestAction testAction) {
		return Functions.getActionUrl(parent.getOwner().getUrl() + "/" + parent.getTestResultAction().getUrlName() + parent.getUrl(), testAction);
	}

	public String getDisplayName() {
		return "Test Stability Report";
	}

	public String getTitle() {
		return getDisplayName();
	}

	public String getName() {
		return "";
	}

	public String getSafeName() {
		return safe(getName());
	}

	public String getSearchUrl() {
		return getSafeName();
	}

	public static String safe(String s) {
		return s.replace('/', '_').replace('\\', '_').replace(':', '_');
	}
}
