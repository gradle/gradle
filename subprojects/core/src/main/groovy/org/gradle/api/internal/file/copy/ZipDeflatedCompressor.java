package org.gradle.api.internal.file.copy;

import java.io.File;
import java.io.IOException;

import org.apache.tools.zip.ZipOutputStream;

public class ZipDeflatedCompressor implements ZipCompressor {
	
	public static final ZipCompressor INSTANCE = new ZipDeflatedCompressor();

	public ZipOutputStream compress(File destination) {
        try {
			ZipOutputStream outStream = new ZipOutputStream(destination);
			outStream.setLevel(ZipOutputStream.DEFLATED);
			return outStream;
		} catch (IOException e) {
            String message = String.format("Unable to create zip output stream for file %s.", destination);
            throw new RuntimeException(message, e);
		}
	}

	private ZipDeflatedCompressor() {}
}
