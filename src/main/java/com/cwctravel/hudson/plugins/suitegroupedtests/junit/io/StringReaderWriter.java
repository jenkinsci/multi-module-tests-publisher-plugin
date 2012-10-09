package com.cwctravel.hudson.plugins.suitegroupedtests.junit.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public class StringReaderWriter implements ReaderWriter {
	private String data;

	public StringReaderWriter() {
		data = "";
	}

	public StringReaderWriter(char[] data, int offset, int length) throws IOException {
		char[] charArray = new char[length - offset];
		System.arraycopy(data, offset, charArray, 0, length);
		this.data = new String(charArray);
	}

	public Reader getReader() throws IOException {
		return new StringReader(data);
	}

	public Writer getWriter() throws IOException {

		return new StringWriter() {
			@Override
			public void flush() {
				StringReaderWriter.this.data = toString();
			}

			@Override
			public void close() {
				StringReaderWriter.this.data = toString();
			}
		};
	}

	public void release() throws IOException {
		data = null;
	}

}
