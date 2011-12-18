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
import org.gradle.util.UncheckedException;

import java.io.File;
import java.io.IOException;

public class DefaultCachedArtifact implements CachedArtifact {
    private final File origin;
    private final String sha1;
    private final long contentLength;
    private final long lastModified;

    public DefaultCachedArtifact(File origin) {
        this.origin = origin;
        this.sha1 = getChecksum(origin);
        contentLength = origin.length();
        lastModified = origin.lastModified();
    }

    public String getSha1() {
        return sha1;
    }

    public File getOrigin() {
        return origin;
    }

    public long getContentLength() {
        return contentLength;
    }

    public long getLastModified() {
        return lastModified;
    }

    private String getChecksum(File contentFile) {
        try {
            return ChecksumHelper.computeAsString(contentFile, "sha1");
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }
}
