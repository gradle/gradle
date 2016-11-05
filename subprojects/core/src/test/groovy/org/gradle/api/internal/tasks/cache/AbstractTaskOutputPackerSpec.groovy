/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.properties.CacheableTaskOutputFilePropertySpec
import org.gradle.api.internal.tasks.properties.TaskPropertySpec
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.tasks.properties.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.properties.CacheableTaskOutputFilePropertySpec.OutputType.FILE

@CleanupTestDirectory(fieldName = "tempDir")
abstract class AbstractTaskOutputPackerSpec extends Specification {
    def taskOutputs = Mock(TaskOutputsInternal)

    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    @ToString
    @EqualsAndHashCode
    protected static class TestProperty implements CacheableTaskOutputFilePropertySpec {
        String propertyName
        File outputFile
        CacheableTaskOutputFilePropertySpec.OutputType outputType
        Class<? extends FileCollectionSnapshotter> snapshotter = GenericFileCollectionSnapshotter

        @Override
        FileCollection getPropertyFiles() {
            new SimpleFileCollection(outputFile)
        }

        @Override
        CacheableTaskOutputFilePropertySpec.OutputType getOutputType() {
            return outputType ?: outputFile.directory ? DIRECTORY : FILE
        }

        @Override
        TaskFilePropertyCompareStrategy getCompareStrategy() {
            TaskFilePropertyCompareStrategy.OUTPUT
        }

        @Override
        SnapshotNormalizationStrategy getSnapshotNormalizationStrategy() {
            TaskFilePropertySnapshotNormalizationStrategy.RELATIVE
        }

        @Override
        int compareTo(TaskPropertySpec o) {
            propertyName <=> o.propertyName
        }
    }

}
