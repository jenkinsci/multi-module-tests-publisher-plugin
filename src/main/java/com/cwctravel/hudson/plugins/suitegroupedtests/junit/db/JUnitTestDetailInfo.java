package com.cwctravel.hudson.plugins.suitegroupedtests.junit.db;

import java.io.Reader;

public class JUnitTestDetailInfo {
	private String errorMessage;
	private String errorStackTrace;
	private Reader stdout;
	private Reader stderr;

	public String getErrorStackTrace() {
		return errorStackTrace;
	}

	public void setErrorStackTrace(String errorStackTrace) {
		this.errorStackTrace = errorStackTrace;
	}

	public Reader getStdout() {
		return stdout;
	}

	public void setStdout(Reader stdout) {
		this.stdout = stdout;
	}

	public Reader getStderr() {
		return stderr;
	}

	public void setStderr(Reader stderr) {
		this.stderr = stderr;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
