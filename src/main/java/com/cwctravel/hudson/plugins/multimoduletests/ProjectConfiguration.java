package com.cwctravel.hudson.plugins.multimoduletests;

import org.kohsuke.stapler.DataBoundConstructor;

public class ProjectConfiguration {
	private boolean keepLongStdio;
	private String testResultFileMask;
	private final String moduleNames;

	@DataBoundConstructor
	public ProjectConfiguration(boolean keepLongStdio, String testResultFileMask, String moduleNames) {
		this.keepLongStdio = keepLongStdio;
		this.testResultFileMask = testResultFileMask;
		this.moduleNames = moduleNames;
	}

	public String getTestResultFileMask() {
		return testResultFileMask;
	}

	public void setTestResultFileMask(String testResultFileMask) {
		this.testResultFileMask = testResultFileMask;
	}

	public boolean isKeepLongStdio() {
		return keepLongStdio;
	}

	public void setKeepLongStdio(boolean keepLongStdio) {
		this.keepLongStdio = keepLongStdio;
	}

	public String getModuleNames() {
		return moduleNames;
	}
}
