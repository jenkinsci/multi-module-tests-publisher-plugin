package com.cwctravel.hudson.plugins.multimoduletests.junit.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class TempFileReaderWriter implements ReaderWriter {
	private final File tempFile;

	public TempFileReaderWriter(char[] data, int offset, int length) throws IOException {
		tempFile = File.createTempFile("junit_", ".dat");
		BufferedWriter bW = new BufferedWriter(new FileWriter(tempFile));
		try {
			bW.write(data, offset, length);
		}
		finally {
			bW.close();
		}
	}

	public Reader getReader() throws IOException {
		return new FileReader(tempFile);
	}

	public Writer getWriter() throws IOException {
		return new FileWriter(tempFile);
	}

	public void release() {
		tempFile.delete();
	}

}
