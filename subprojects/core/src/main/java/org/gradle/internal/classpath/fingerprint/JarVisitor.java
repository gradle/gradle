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
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.io.IoFunction;
import org.gradle.util.internal.JarUtil;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Hashes JARs of the buildscript classpath.
 */
class JarVisitor implements ZipHasher.ArchiveVisitor {
    private final int currentJvmMajor;
    private final ResourceHasher resourceHasher;

    public JarVisitor(int currentJvmMajor, ResourceHasher resourceHasher) {
        this.currentJvmMajor = currentJvmMajor;
        this.resourceHasher = resourceHasher;
    }

    @Nullable
    @Override
    public HashCode visitArchive(IoFunction<ZipHasher.EntryVisitor, Hasher> visitAction) throws IOException {
        CollectingEntryVisitor entryVisitor = new CollectingEntryVisitor(resourceHasher);
        @Nullable Hasher hasher = visitAction.apply(entryVisitor);
        if (hasher != null) {
            hasher.putBoolean(entryVisitor.hasLoadableUnsupportedVersionedDir());
            return hasher.hash();
        }
        return null;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        // Fingerprints are computed differently for supported and unsupported (too new) JVM versions.
        hasher.putBoolean(isUnsupportedVersion(currentJvmMajor));
        resourceHasher.appendConfigurationToHasher(hasher);
    }

    private class CollectingEntryVisitor implements ZipHasher.EntryVisitor {
        private final ResourceHasher resourceHasher;
        @Nullable
        private Boolean isMultiReleaseJar;
        private boolean hasLoadableUnsupportedVersionDirectory;

        public CollectingEntryVisitor(ResourceHasher resourceHasher) {
            this.resourceHasher = resourceHasher;
        }

        @Nullable
        @Override
        public HashCode visitEntry(ZipEntryContext zipEntryContext) throws IOException {
            String entryPath = zipEntryContext.getFullName();

            if (isMultiReleaseJar == null && JarUtil.isManifestName(entryPath)) {
                isMultiReleaseJar = JarUtil.isMultiReleaseJarManifest(JarUtil.readManifest(zipEntryContext.getEntry().getContent()));
            } else if (!hasLoadableUnsupportedVersionDirectory && mayBeInMultiReleaseJar()) {
                JarUtil.getVersionedDirectoryMajorVersion(entryPath).ifPresent(
                    this::visitVersionedEntry
                );
            }
            return resourceHasher.hash(zipEntryContext);
        }

        /**
         * Returns true if we can be processing the multi-release JAR, either because it has proper manifest or we haven't seen the manifest yet.
         */
        private boolean mayBeInMultiReleaseJar() {
            return isMultiReleaseJar == null || isMultiReleaseJar;
        }

        private void visitVersionedEntry(int majorJavaVersion) {
            hasLoadableUnsupportedVersionDirectory |= isVersionedEntryLoadableOnCurrentJvm(majorJavaVersion) && isUnsupportedVersion(majorJavaVersion);
        }

        private boolean isVersionedEntryLoadableOnCurrentJvm(int majorJavaVersion) {
            return majorJavaVersion <= currentJvmMajor;
        }

        public boolean hasLoadableUnsupportedVersionedDir() {
            return isMultiReleaseJar != null && isMultiReleaseJar && hasLoadableUnsupportedVersionDirectory;
        }
    }

    private static boolean isUnsupportedVersion(int majorJavaVersion) {
        return majorJavaVersion > AsmConstants.MAX_SUPPORTED_JAVA_VERSION;
    }
}
