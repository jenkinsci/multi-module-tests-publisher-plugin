package com.cwctravel.hudson.plugins.multimoduletests.junit.io;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class IOUtil {
	public static ReaderWriter createReaderWriter(char[] data, int offset, int length) throws IOException {
		ReaderWriter result = null;
		if(length < 1024 * 1024) {
			result = new StringReaderWriter(data, offset, length);
		}
		else {
			result = new TempFileReaderWriter(data, offset, length);
		}
		return result;
	}

	public static void write(Reader reader, Writer writer) throws IOException {
		if(reader != null && writer != null) {
			char[] buffer = new char[1024];
			int bytesRead = 0;
			while((bytesRead = reader.read(buffer)) >= 0) {
				writer.write(buffer, 0, bytesRead);
			}
			writer.flush();
		}
	}
}
