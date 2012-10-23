package com.cwctravel.hudson.plugins.multimoduletests.junit;

public class JUnitException extends RuntimeException {
	private static final long serialVersionUID = -3907992484744887999L;

	public JUnitException() {
		super();
	}

	public JUnitException(String message, Throwable cause) {
		super(message, cause);
	}

	public JUnitException(String message) {
		super(message);
	}

	public JUnitException(Throwable cause) {
		super(cause);
	}

}
