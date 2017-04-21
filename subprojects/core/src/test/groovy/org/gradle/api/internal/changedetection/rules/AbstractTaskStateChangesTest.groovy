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

package org.gradle.api.internal.changedetection.rules

import com.google.common.collect.ImmutableSortedSet
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.snapshotting.SnapshottingConfigurationInternal
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.SnapshotNormalizationStrategy
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec
import org.gradle.api.internal.tasks.TaskPropertySpec
import spock.lang.Specification

abstract class AbstractTaskStateChangesTest extends Specification {
    protected mockInputs = Mock(TaskInputsInternal)
    protected mockOutputs = Mock(TaskOutputsInternal)
    protected mockSnapshottingConfiguration = Mock(SnapshottingConfigurationInternal)
    protected stubProject = Stub(ProjectInternal) {
        getSnapshotting() >> mockSnapshottingConfiguration
    }
    protected TaskInternal stubTask

    def setup() {
        stubTask = Stub(TaskInternal) {
            getName() >> { "testTask" }
            getInputs() >> mockInputs
            getOutputs() >> mockOutputs
            getProject() >> stubProject
        }
    }

    protected static def fileProperties(Map<String, String> props) {
        return ImmutableSortedSet.copyOf(props.collect { entry ->
            return new PropertySpec(
                propertyName: entry.key,
                propertyFiles: new SimpleFileCollection([new File(entry.value)]),
                compareStrategy: TaskFilePropertyCompareStrategy.UNORDERED,
                snapshotNormalizationStrategy: TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE
            )
        })
    }

    protected static class PropertySpec implements TaskInputFilePropertySpec {
        String propertyName
        FileCollection propertyFiles
        TaskFilePropertyCompareStrategy compareStrategy
        SnapshotNormalizationStrategy snapshotNormalizationStrategy
        Class<? extends FileCollectionSnapshotter> snapshotter = GenericFileCollectionSnapshotter

        @Override
        int compareTo(TaskPropertySpec o) {
            return propertyName.compareTo(o.propertyName)
        }

        @Override
        boolean isSkipWhenEmpty() {
            return false
        }
    }
}
