/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.filestore;

import org.apache.ivy.util.ChecksumHelper;
import org.gradle.api.GradleException;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.util.UncheckedException;
import org.jfrog.wharf.ivy.util.WharfUtils;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class DefaultFileStore implements FileStore {
    private final File baseDir;
    private final Random generator = new Random(System.currentTimeMillis());

    public DefaultFileStore(ArtifactCacheMetaData cacheMetaData) {
        this.baseDir = new File(cacheMetaData.getCacheDir(), "filestore");
    }

    public File add(File contentFile) {
        String checksum = getChecksum(contentFile);
        File storageFile = getStorageFile(checksum);
        if (!storageFile.exists()) {
            saveIntoFileStore(contentFile, storageFile);
        }
        return storageFile;
    }

    private void saveIntoFileStore(File contentFile, File storageFile) {
        if (!contentFile.renameTo(storageFile)) {
            throw new GradleException(String.format("Failed to copy downloaded content into storage file: %s", storageFile));
        }
    }

    private String getChecksum(File contentFile) {
        try {
            return ChecksumHelper.computeAsString(contentFile, "sha1");
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private File getStorageFile(String checksum) {
        checksum = WharfUtils.getCleanChecksum(checksum);
        File storageFile = new File(baseDir, checksum.substring(0, 3) + "/" + checksum);
        if (!storageFile.getParentFile().exists()) {
            storageFile.getParentFile().mkdirs();
        }
        return storageFile;
    }

    public File getTempFile() {
        long tempLong = generator.nextLong();
        tempLong = tempLong < 0 ? -tempLong : tempLong;
        return new File(baseDir, "temp/" + tempLong);
    }
}
