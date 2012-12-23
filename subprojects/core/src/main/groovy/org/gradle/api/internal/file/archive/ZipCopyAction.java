package org.gradle.api.internal.file.archive;

import org.gradle.api.internal.file.copy.ArchiveCopyAction;
import org.gradle.api.internal.file.copy.ZipCompressor;

public interface ZipCopyAction extends ArchiveCopyAction {

	public ZipCompressor getCompressor();
}
