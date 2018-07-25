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
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec
import org.gradle.api.internal.tasks.TaskPropertySpec
import org.gradle.api.tasks.FileNormalizer
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer
import org.gradle.normalization.internal.InputNormalizationHandlerInternal
import spock.lang.Specification

abstract class AbstractTaskStateChangesTest extends Specification {
    protected mockInputs = Mock(TaskInputsInternal)
    protected mockOutputs = Mock(TaskOutputsInternal)
    protected TaskInternal stubTask
    protected stubProject = Stub(Project) {
        getNormalization() >> Stub(InputNormalizationHandlerInternal)
    }

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
                propertyFiles: ImmutableFileCollection.of(new File(entry.value)),
            )
        })
    }

    protected static class PropertySpec implements TaskInputFilePropertySpec {
        String propertyName
        FileCollection propertyFiles
        Class<? extends FileNormalizer> normalizer = AbsolutePathInputNormalizer.class

        @Override
        int compareTo(TaskPropertySpec o) {
            return propertyName.compareTo(o.propertyName)
        }

        @Override
        boolean isSkipWhenEmpty() {
            return false
        }

        @Override
        void prepareValue() {
        }

        @Override
        void cleanupValue() {
        }
    }
}
