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
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService
import org.gradle.api.internal.changedetection.state.ZipHasher
import org.gradle.api.tasks.CompileClasspathNormalizer
import org.gradle.api.tasks.FileNormalizer
import org.gradle.internal.execution.fingerprint.FileCollectionSnapshotter
import org.gradle.internal.fingerprint.classpath.CompileClasspathFingerprinter
import org.gradle.internal.fingerprint.classpath.impl.ClasspathFingerprintingStrategy
import org.gradle.internal.fingerprint.impl.AbstractFileCollectionFingerprinter
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.kotlin.dsl.support.loggerFor


class KotlinCompileClasspathFingerprinter(
    cacheService: ResourceSnapshotterCacheService,
    fileCollectionSnapshotter: FileCollectionSnapshotter,
    stringInterner: StringInterner
) : AbstractFileCollectionFingerprinter(
    ClasspathFingerprintingStrategy.compileClasspath(
        CachingResourceHasher(AbiExtractingClasspathResourceHasher(KotlinApiClassExtractor()), cacheService),
        cacheService,
        stringInterner,
        CompileAvoidanceExceptionReporter()
    ),
    fileCollectionSnapshotter
),
    CompileClasspathFingerprinter {

    override fun getRegisteredType(): Class<out FileNormalizer> {
        return CompileClasspathNormalizer::class.java
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
