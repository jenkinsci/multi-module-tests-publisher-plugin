package com.cwctravel.hudson.plugins.suitegroupedtests;

import org.kohsuke.stapler.DataBoundConstructor;

public class SuiteTestGroupConfiguration {
	private boolean keepLongStdio;
	private String testResultFileMask;

	@DataBoundConstructor
	public SuiteTestGroupConfiguration(boolean keepLongStdio, String testResultFileMask) {
		this.keepLongStdio = keepLongStdio;
		this.testResultFileMask = testResultFileMask;
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

}
