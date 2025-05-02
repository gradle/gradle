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

package org.gradle.internal.fingerprint.classpath.impl;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.RuntimeClasspathResourceHasher;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.AbstractFileCollectionFingerprinter;

import java.util.Map;

public class DefaultClasspathFingerprinter extends AbstractFileCollectionFingerprinter implements ClasspathFingerprinter {
    public DefaultClasspathFingerprinter(
        ResourceSnapshotterCacheService cacheService,
        FileCollectionSnapshotter fileCollectionSnapshotter,
        ResourceFilter classpathResourceFilter,
        ResourceEntryFilter manifestAttributeResourceEntryFilter,
        Map<String, ResourceEntryFilter> propertiesFileFilters,
        StringInterner stringInterner,
        LineEndingSensitivity lineEndingSensitivity
    ) {
        super(
            ClasspathFingerprintingStrategy.runtimeClasspath(
                classpathResourceFilter,
                manifestAttributeResourceEntryFilter,
                propertiesFileFilters,
                new RuntimeClasspathResourceHasher(),
                cacheService,
                stringInterner,
                lineEndingSensitivity
            ),
            fileCollectionSnapshotter
        );
    }

    @Override
    public FileNormalizer getNormalizer() {
        return InputNormalizer.RUNTIME_CLASSPATH;
    }
}
