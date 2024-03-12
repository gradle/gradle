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

package org.gradle.performance.mutator


import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.internal.classpath.TransformedClassPath.INSTRUMENTED_MARKER_FILE_NAME
import static org.gradle.profiler.mutations.AbstractCleanupMutator.CleanupSchedule.BUILD

class ClearArtifactTransformCacheWithoutInstrumentedJarsMutatorTest extends Specification {

    @TempDir
    File gradleUserHome

    def "should cleanup all folders except the ones with instrumented jars"() {
        given:
        createFile(new File(gradleUserHome, "caches/transforms-1/first/transformed/file"))
        createFile(new File(gradleUserHome, "caches/transforms-1/first/metadata.bin"))
        createFile(new File(gradleUserHome, "caches/transforms-1/first/transformed/instrumented/file"))
        createFile(new File(gradleUserHome, "caches/transforms-1/second/transformed/original/file"))
        createFile(new File(gradleUserHome, "caches/transforms-2/first/transformed/instrumented/file"))
        createFile(new File(gradleUserHome, "caches/transforms-2/first/transformed/original/file"))
        createFile(new File(gradleUserHome, "caches/transforms-2/second/metadata.bin"))
        createFile(new File(gradleUserHome, "caches/transforms-2/second/transformed/$INSTRUMENTED_MARKER_FILE_NAME"))
        createFile(new File(gradleUserHome, "caches/transforms-2/second/transformed/instrumented/file"))
        createFile(new File(gradleUserHome, "caches/transforms-2/second/transformed/original/file"))
        def mutator = new ClearArtifactTransformCacheWithoutInstrumentedJarsMutator(gradleUserHome, BUILD)

        when:
        mutator.beforeBuild(null)

        then:
        !new File(gradleUserHome, "caches/transforms-1/").exists()
        !new File(gradleUserHome, "caches/transforms-2/first").exists()
        new File(gradleUserHome, "caches/transforms-2/second/metadata.bin").exists()
        new File(gradleUserHome, "caches/transforms-2/second/transformed/$INSTRUMENTED_MARKER_FILE_NAME").exists()
        new File(gradleUserHome, "caches/transforms-2/second/transformed/instrumented/file").exists()
        new File(gradleUserHome, "caches/transforms-2/second/transformed/original/file").exists()
    }

    private static void createFile(File file) {
        file.parentFile.mkdirs()
        file.createNewFile()
    }
}
