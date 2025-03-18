/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.tasks.bundling;

import org.gradle.api.internal.file.archive.TarCopyAction;
import org.gradle.api.internal.file.archive.compression.ArchiveOutputStreamFactory;
import org.gradle.api.internal.file.archive.compression.Bzip2Archiver;
import org.gradle.api.internal.file.archive.compression.GzipArchiver;
import org.gradle.api.internal.file.archive.compression.SimpleCompressor;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

/**
 * Assembles a TAR archive.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class Tar extends AbstractArchiveTask {

    public Tar() {
        getCompression().convention(Compression.NONE);
        getArchiveExtension().set(getCompression().map(Compression::getDefaultExtension));
    }

    @Override
    protected CopyAction createCopyAction() {
        return new TarCopyAction(
            getArchiveFile().get().getAsFile(),
            getCompressor(),
            getPreserveFileTimestamps().get()
        );
    }

    private ArchiveOutputStreamFactory getCompressor() {
        switch(getCompression().get()) {
            case BZIP2: return Bzip2Archiver.getCompressor();
            case GZIP:  return GzipArchiver.getCompressor();
            default:    return new SimpleCompressor();
        }
    }

    /**
     * Returns the compression that is used for this archive.
     *
     * @return The compression. Never returns null.
     */
    @Input
    @ReplacesEagerProperty
    public abstract Property<Compression> getCompression();
}
