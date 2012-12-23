package org.gradle.api.internal.file.copy;

import java.io.File;

import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.internal.file.archive.compression.Compressor;

public interface ZipCompressor extends Compressor {

	ZipOutputStream compress(File destination);
}
