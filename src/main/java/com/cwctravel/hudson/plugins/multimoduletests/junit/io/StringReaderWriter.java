package com.cwctravel.hudson.plugins.multimoduletests.junit.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

public class StringReaderWriter implements ReaderWriter {
	private StringBuilder data;

	public StringReaderWriter() {
		data = new StringBuilder();
	}

	public StringReaderWriter(char[] data, int offset, int length) throws IOException {
		char[] charArray = new char[length - offset];
		System.arraycopy(data, offset, charArray, 0, length);
		this.data = new StringBuilder(new String(charArray));
	}

	public Reader getReader() throws IOException {
		return new StringReader(data.toString());
	}

	public Writer getWriter() throws IOException {

		return new Writer() {

			@Override
			public void write(char[] data, int offset, int length) throws IOException {
				StringReaderWriter.this.data.append(data, offset, length);

			}

			@Override
			public void flush() throws IOException {

			}

			@Override
			public void close() throws IOException {

			}

		};
	}

	public void release() throws IOException {
		data = null;
	}

}
