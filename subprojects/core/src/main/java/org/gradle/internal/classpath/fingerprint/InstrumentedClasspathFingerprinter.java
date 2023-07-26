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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.JavaVersionParser;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.PropertiesFileFilter;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.RuntimeClasspathResourceHasher;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.classpath.impl.ClasspathFingerprintingStrategy;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.impl.AbstractFileCollectionFingerprinter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import javax.annotation.Nullable;

/**
 * Custom implementation of a runtime classpath fingerprinter to use with instrumented build script classpath JARs.
 * It takes into account how the instrumentation works when computing the JAR fingerprint.
 */
@ServiceScope(Scopes.UserHome.class)
public class InstrumentedClasspathFingerprinter extends AbstractFileCollectionFingerprinter {
    public InstrumentedClasspathFingerprinter(ResourceSnapshotterCacheService cacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
        this(JavaVersionParser.getMajorFromVersionString(SystemProperties.getInstance().getJavaVersion()), cacheService, fileCollectionSnapshotter, stringInterner);
    }

    @VisibleForTesting
    InstrumentedClasspathFingerprinter(int currentJvmMajor, ResourceSnapshotterCacheService cacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
        super(fingerprintingStrategy(currentJvmMajor, cacheService, stringInterner), fileCollectionSnapshotter);
    }

    @Override
    public CurrentFileCollectionFingerprint fingerprint(FileSystemSnapshot snapshot, @Nullable FileCollectionFingerprint previousFingerprint) {
        return super.fingerprint(snapshot, previousFingerprint);
    }

    @Override
    public FileNormalizer getNormalizer() {
        return InputNormalizer.RUNTIME_CLASSPATH;
    }

    private static ClasspathFingerprintingStrategy fingerprintingStrategy(int currentJvmMajor, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        ResourceHasher resourceHasher = ClasspathFingerprintingStrategy.runtimeClasspathResourceHasher(
            new RuntimeClasspathResourceHasher(),
            LineEndingSensitivity.DEFAULT,
            PropertiesFileFilter.FILTER_NOTHING,
            ResourceEntryFilter.FILTER_NOTHING,
            ResourceFilter.FILTER_NOTHING
        );

        return ClasspathFingerprintingStrategy.runtimeClassPathWithSpecialJarHandling(new JarVisitor(currentJvmMajor, resourceHasher), resourceHasher, cacheService, stringInterner);
    }
}
