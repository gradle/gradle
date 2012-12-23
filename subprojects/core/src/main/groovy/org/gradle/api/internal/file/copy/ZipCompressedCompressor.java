package org.gradle.api.internal.file.copy;

import java.io.File;
import java.io.IOException;

import org.apache.tools.zip.ZipOutputStream;

public class ZipCompressedCompressor implements ZipCompressor {
	
	public static final ZipCompressor INSTANCE = new ZipCompressedCompressor();

	public ZipOutputStream compress(File destination) {
        try {
			return new ZipOutputStream(destination);
		} catch (IOException e) {
            String message = String.format("Unable to create zip output stream for file %s.", destination);
            throw new RuntimeException(message, e);
		}
	}

	private ZipCompressedCompressor() {}
}
