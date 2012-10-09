package com.cwctravel.hudson.plugins.suitegroupedtests.junit.io;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public interface ReaderWriter {
	public Reader getReader() throws IOException;

	public Writer getWriter() throws IOException;

	public void release() throws IOException;

}
