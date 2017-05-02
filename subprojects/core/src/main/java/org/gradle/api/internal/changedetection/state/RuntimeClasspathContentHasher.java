/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;

/**
 * Hashes contents of resources ({@link RegularFileSnapshot}s and {@link ZipEntry}s) in runtime classpath entries.
 *
 * Currently, we take the unmodified content into account but we could be smarter at some point.
 */
public class RuntimeClasspathContentHasher implements ContentHasher {
    private final Set<Spec<RelativePath>> ignoreSpecs;

    public RuntimeClasspathContentHasher(Set<Spec<RelativePath>> ignoreSpecs) {
        this.ignoreSpecs = ignoreSpecs;
    }

    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        if (shouldIgnore(fileSnapshot.getRelativePath())) {
            return null;
        }
        return fileSnapshot.getContent().getContentMd5();
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        if (shouldIgnore(RelativePath.parse(true, zipEntry.getName()))) {
            return null;
        }
        Hasher hasher = Hashing.md5().newHasher();
        ByteStreams.copy(zipInput, Funnels.asOutputStream(hasher));
        return hasher.hash();
    }

    private boolean shouldIgnore(RelativePath relativePath) {
        if (ignoreSpecs.isEmpty()) {
            return false;
        }
        for (Spec<RelativePath> ignoreSpec : ignoreSpecs) {
            if (ignoreSpec.isSatisfiedBy(relativePath)) {
                return true;
            }
        }
        return false;
    }
}
