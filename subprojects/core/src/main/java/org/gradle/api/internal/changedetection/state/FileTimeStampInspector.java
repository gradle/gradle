/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Attempts to detect certain kinds of changes to files that are not always visible using file timestamp and length:
 * Attempts to detect changes to files made immediately after the previous build, as these files may have the same timestamp as when we observed the old content.
 *
 * Some common use cases that causes these kinds of changes are functional testing and benchmarking, were the test runs a build, modifies some file and then runs the build again.
 *
 * The detection is done by updating a marker file at the end of the build. In the next build, consider any files whose timestamp is the same as that of this marker file as potentially changed and hash their contents.
 *
 * This strategy could be improved in several ways:
 *  - Don't use the timestamp for files that we've already hashed during this build.
 *  - Potentially only apply the end-of-build timestamp for input files only, as often some or all of the output files of a build will have the end-of-build timestamp.
 *  - Use finer grained timestamps, where available. Currently we still use the `File.lastModified()` timestamp on some platforms.
 */
public abstract class FileTimeStampInspector {
    private final File workDir;
    private final File markerFile;
    private final TemporaryFileProvider temporaryFileProvider;
    private long lastBuildTimestamp;

    protected FileTimeStampInspector(File workDir) {
        this.workDir = workDir;
        this.markerFile = new File(workDir, "last-build.bin");
        this.temporaryFileProvider = new DefaultTemporaryFileProvider(() -> workDir);
    }

    public long getLastBuildTimestamp() {
        return lastBuildTimestamp;
    }

    protected void updateOnStartBuild() {
        workDir.mkdirs();

        if (markerFile.exists()) {
            lastBuildTimestamp = timestampOfMarkerFile();
        } else {
            lastBuildTimestamp = 0;
        }
    }

    protected void updateOnFinishBuild() {
        workDir.mkdirs();
        try {
            try (FileOutputStream outputStream = new FileOutputStream(markerFile)) {
                outputStream.write(0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update " + markerFile, e);
        }

        lastBuildTimestamp = timestampOfMarkerFile();
    }

    protected long currentTimestamp() {
        File file = temporaryFileProvider.createTemporaryFile("this-build", "bin");
        try {
            return timestampOf(file);
        } finally {
            file.delete();
        }
    }

    /**
     * Returns true if the given file timestamp can be used to detect a file change.
     */
    public boolean timestampCanBeUsedToDetectFileChange(String file, long timestamp) {
        // Do not use a timestamp that is the same as the end of the last build or the start of this build
        return timestamp != lastBuildTimestamp;
    }

    private long timestampOfMarkerFile() {
        return timestampOf(markerFile);
    }

    private long timestampOf(File file) {
        return file.lastModified();
    }
}
