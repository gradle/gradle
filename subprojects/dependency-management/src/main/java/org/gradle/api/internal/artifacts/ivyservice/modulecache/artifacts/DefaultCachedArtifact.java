/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class DefaultCachedArtifact implements CachedArtifact, Serializable {
    private final File cachedFile;
    private final long cachedAt;
    private final BigInteger descriptorHash;
    private final List<String> attemptedLocations;

    public DefaultCachedArtifact(File cachedFile, long cachedAt, BigInteger descriptorHash) {
        this.cachedFile = cachedFile;
        this.cachedAt = cachedAt;
        this.descriptorHash = descriptorHash;
        this.attemptedLocations = Collections.emptyList();
    }

    public DefaultCachedArtifact(List<String> attemptedLocations, long cachedAt, BigInteger descriptorHash) {
        this.attemptedLocations = attemptedLocations;
        this.cachedAt = cachedAt;
        this.cachedFile = null;
        this.descriptorHash = descriptorHash;
    }

    @Override
    public boolean isMissing() {
        return cachedFile == null;
    }

    @Override
    public File getCachedFile() {
        return cachedFile;
    }

    @Override
    public long getCachedAt() {
        return cachedAt;
    }

    @Override
    public BigInteger getDescriptorHash() {
        return descriptorHash;
    }

    @Override
    public List<String> attemptedLocations() {
        return attemptedLocations;
    }
}
