/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.fingerprint;

import org.gradle.api.internal.changedetection.state.ZipHasher;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.io.IoFunction;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Hashes JARs of the buildscript classpath.
 */
class JarVisitor implements ZipHasher.ArchiveVisitor {
    private final ResourceHasher resourceHasher;

    public JarVisitor(ResourceHasher resourceHasher) {
        this.resourceHasher = resourceHasher;
    }

    @Nullable
    @Override
    public HashCode visitArchive(IoFunction<ZipHasher.EntryVisitor, Hasher> visitAction) throws IOException {
        CollectingEntryVisitor entryVisitor = new CollectingEntryVisitor(resourceHasher);
        @Nullable Hasher hasher = visitAction.apply(entryVisitor);
        return hasher != null ? hasher.hash() : null;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        resourceHasher.appendConfigurationToHasher(hasher);
    }

    private static class CollectingEntryVisitor implements ZipHasher.EntryVisitor {
        private final ResourceHasher resourceHasher;

        public CollectingEntryVisitor(ResourceHasher resourceHasher) {
            this.resourceHasher = resourceHasher;
        }

        @Nullable
        @Override
        public HashCode visitEntry(ZipEntryContext zipEntryContext) throws IOException {
            return resourceHasher.hash(zipEntryContext);
        }
    }
}
