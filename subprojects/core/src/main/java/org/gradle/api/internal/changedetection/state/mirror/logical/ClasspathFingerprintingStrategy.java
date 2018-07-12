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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.api.internal.changedetection.state.JarHasher;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathHolder;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathTracker;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;

public class ClasspathFingerprintingStrategy implements FingerprintingStrategy {

    private final NonJarFingerprintingStrategy nonJarFingerprintingStrategy;
    private final ResourceSnapshotterCacheService cacheService;
    private final ResourceHasher classpathResourceHasher;
    private final JarHasher jarHasher;
    private final StringInterner stringInterner;
    private final HashCode jarHasherConfigurationHash;

    public ClasspathFingerprintingStrategy(NonJarFingerprintingStrategy nonJarFingerprintingStrategy, ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        this.nonJarFingerprintingStrategy = nonJarFingerprintingStrategy;
        this.cacheService = cacheService;
        this.classpathResourceHasher = classpathResourceHasher;
        this.jarHasher = new JarHasher(classpathResourceHasher);
        this.stringInterner = stringInterner;
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        jarHasher.appendConfigurationToHasher(hasher);
        this.jarHasherConfigurationHash = hasher.hash();
    }

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<PhysicalSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (PhysicalSnapshot root : roots) {
            final ImmutableSortedMap.Builder<String, NormalizedFileSnapshot> rootBuilder = ImmutableSortedMap.naturalOrder();
            root.accept(new ClasspathSnapshottingVisitor(new PhysicalSnapshotVisitor() {
                private final RelativePathHolder relativePathHolder = new RelativePathHolder();

                @Override
                public boolean preVisitDirectory(String absolutePath, String name) {
                    relativePathHolder.enter(name);
                    return true;
                }

                @Override
                public void visit(String absolutePath, String name, FileContentSnapshot content) {
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot normalizedFileSnapshot = relativePathHolder.isRoot() ? content : createNormalizedSnapshot(name, content);
                        rootBuilder.put(
                            absolutePath,
                            normalizedFileSnapshot);
                    }
                }

                private NormalizedFileSnapshot createNormalizedSnapshot(String name, FileContentSnapshot content) {
                    relativePathHolder.enter(name);
                    NormalizedFileSnapshot normalizedFileSnapshot = new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathHolder.getRelativePathString()), content);
                    relativePathHolder.leave();
                    return normalizedFileSnapshot;
                }

                @Override
                public void postVisitDirectory() {
                    relativePathHolder.leave();
                }
            }));
            builder.putAll(rootBuilder.build());
        }
        return builder.build();
    }

    public enum NonJarFingerprintingStrategy {
        IGNORE {
            @Nullable
            @Override
            public FileContentSnapshot determineNonJarFingerprint(FileContentSnapshot original) {
                return null;
            }
        },
        USE_FILE_HASH {
            @Override
            public FileContentSnapshot determineNonJarFingerprint(FileContentSnapshot original) {
                return original;
            }
        };

        @Nullable
        public abstract FileContentSnapshot determineNonJarFingerprint(FileContentSnapshot original);
    }

    private class ClasspathSnapshottingVisitor implements PhysicalSnapshotVisitor {

        private final PhysicalSnapshotVisitor delegate;
        private RelativePathTracker relativePathTracker = new RelativePathTracker();

        public ClasspathSnapshottingVisitor(PhysicalSnapshotVisitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean preVisitDirectory(String absolutePath, String name) {
            relativePathTracker.enter(name);
            return delegate.preVisitDirectory(absolutePath, name);
        }

        @Override
        public void visit(String absolutePath, String name, FileContentSnapshot content) {
            if (content.getType() == FileType.RegularFile) {
                FileContentSnapshot newContent = fingerprintFile(absolutePath, name, content);
                if (newContent != null) {
                    delegate.visit(absolutePath, name, newContent);
                }
            }
        }

        @Nullable
        private FileContentSnapshot fingerprintFile(String absolutePath, String name, FileContentSnapshot content) {
            return relativePathTracker.isRoot() ? fingerprintRootFile(absolutePath, name, content) : fingerprintTreeFile(absolutePath, name, content);
        }

        @Nullable
        private FileContentSnapshot fingerprintTreeFile(String absolutePath, String name, FileContentSnapshot content) {
            relativePathTracker.enter(name);
            HashCode newHash = classpathResourceHasher.hash(absolutePath, relativePathTracker.getRelativePath(), content);
            relativePathTracker.leave();
            return newHash == null ? null : new FileHashSnapshot(newHash);
        }

        @Override
        public void postVisitDirectory() {
            relativePathTracker.leave();
            delegate.postVisitDirectory();
        }
    }

    @Nullable
    private FileContentSnapshot fingerprintRootFile(String absolutePath, String name, FileContentSnapshot content) {
        if (FileUtils.hasExtensionIgnoresCase(name, ".jar")) {
            return snapshotJarContents(absolutePath, ImmutableList.of(name), content);
        }
        return nonJarFingerprintingStrategy.determineNonJarFingerprint(content);
    }

    @Nullable
    private FileContentSnapshot snapshotJarContents(String absolutePath, Iterable<String> relativePath, FileContentSnapshot contentSnapshot) {
        HashCode hash = cacheService.hashFile(absolutePath, relativePath, contentSnapshot, jarHasher, jarHasherConfigurationHash);
        return hash == null ? null : new FileHashSnapshot(hash);
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.CLASSPATH;
    }
}
