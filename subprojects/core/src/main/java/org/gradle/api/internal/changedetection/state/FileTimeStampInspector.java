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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.initialization.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Attempts to detect changes made immediately after the previous build. It does this by updating a marker file at the end of the build. In the next build, consider any files whose timestamp is the same as that of this marker file as potentially changed and hash their contents.
 */
public class FileTimeStampInspector extends BuildAdapter {
    private File markerFile;
    private long lastBuildTimestamp;

    @Override
    public void settingsEvaluated(Settings settings) {
        markerFile = new File(settings.getRootDir(), ".gradle/file-change-marker");
        if (markerFile.exists()) {
            lastBuildTimestamp = markerFile.lastModified();
        } else {
            lastBuildTimestamp = 0;
        }
    }

    @Override
    public void buildFinished(BuildResult result) {
        if (markerFile == null) {
            return;
        }

        markerFile.getParentFile().mkdirs();
        try {
            FileOutputStream outputStream = new FileOutputStream(markerFile);
            try {
                outputStream.write(0);
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not update " + markerFile, e);
        }
        lastBuildTimestamp = markerFile.lastModified();
        markerFile = null;
    }

    /**
     * Returns true if the given file timestamp can be used to detect a file change.
     */
    boolean timestampCanBeUsedToDetectFileChange(long timestamp) {
        // Do not use a timestamp that is the same as the end of the last build
        return timestamp != lastBuildTimestamp || timestamp == 0;
    }
}
