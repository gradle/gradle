/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.kotlin.dsl.normalization

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.AbiExtractingClasspathResourceHasher
import org.gradle.api.internal.changedetection.state.CachingResourceHasher
import org.gradle.api.internal.changedetection.state.PropertiesFileFilter
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter
import org.gradle.api.internal.changedetection.state.ResourceFilter
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService
import org.gradle.api.internal.changedetection.state.RuntimeClasspathResourceHasher
import org.gradle.api.internal.changedetection.state.ZipHasher
import org.gradle.internal.execution.FileCollectionSnapshotter
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.fingerprint.classpath.CompileClasspathFingerprinter
import org.gradle.internal.fingerprint.classpath.impl.ClasspathFingerprintingStrategy
import org.gradle.internal.fingerprint.impl.AbstractFileCollectionFingerprinter
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.kotlin.dsl.support.loggerFor


internal
class KotlinCompileClasspathFingerprinter(
    cacheService: ResourceSnapshotterCacheService,
    fileCollectionSnapshotter: FileCollectionSnapshotter,
    stringInterner: StringInterner
) : AbstractFileCollectionFingerprinter(
    ClasspathFingerprintingStrategy.compileClasspathFallbackToRuntimeClasspath(
        CachingResourceHasher(
            AbiExtractingClasspathResourceHasher.withoutFallback(KotlinApiClassExtractor()),
            cacheService
        ),
        ClasspathFingerprintingStrategy.runtimeClasspathResourceHasher(
            RuntimeClasspathResourceHasher(),
            LineEndingSensitivity.DEFAULT,
            PropertiesFileFilter.FILTER_NOTHING,
            ResourceEntryFilter.FILTER_NOTHING,
            ResourceFilter.FILTER_NOTHING
        ),
        cacheService,
        stringInterner,
        CompileAvoidanceExceptionReporter()
    ),
    fileCollectionSnapshotter
),
    CompileClasspathFingerprinter {

    override fun getNormalizer(): FileNormalizer {
        return InputNormalizer.COMPILE_CLASSPATH
    }
}


private
class CompileAvoidanceExceptionReporter : ZipHasher.HashingExceptionReporter {
    override fun report(zipFileSnapshot: RegularFileSnapshot, e: Exception) {
        if (e is CompileAvoidanceException) {
            val jarPath = zipFileSnapshot.absolutePath
            logger.info("Cannot use Kotlin build script compile avoidance with {}: {}", jarPath, e.message)
        }
    }
}


internal
val logger = loggerFor<KotlinCompileClasspathFingerprinter>()
