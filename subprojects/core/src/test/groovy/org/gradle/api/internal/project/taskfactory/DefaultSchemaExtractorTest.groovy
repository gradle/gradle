/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.TaskInternal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import java.util.concurrent.Callable

import static org.gradle.api.internal.project.taskfactory.AnnotationProcessingTasks.*

class DefaultSchemaExtractorTest extends AbstractProjectBuilderSpec {

    private Map args = new HashMap()
    DefaultSchemaExtractor extractor = new DefaultSchemaExtractor()
    ITaskFactory delegate
    DefaultTaskClassInfoStore taskClassInfoStore
    AnnotationProcessingTaskFactory factory

    def setup() {
        delegate = Mock(ITaskFactory)
        taskClassInfoStore = new DefaultTaskClassInfoStore(new DefaultTaskClassValidatorExtractor())
        factory = new AnnotationProcessingTaskFactory(taskClassInfoStore, delegate)
    }

    def "extract inputs and outputs from class"() {
        when:
        def classInfo = extractor.extractClassInfo(type)

        then:
        classInfo.annotatedProperties.collectEntries() { [(it.name): it.propertyType] } == [(propertyName): propertyType]

        where:
        type                       | propertyName | propertyType
        TaskWithInputDir           | 'inputDir'   | InputDirectory
        TaskWithOutputDir          | 'outputDir'  | OutputDirectory
        TaskWithBooleanInput       | 'inputValue' | Input
        TaskWithOptionalOutputFile | 'outputFile' | OutputFile
        TaskWithNestedBean         | 'bean'       | Nested
    }

    def "extract schema from class"() {
        when:
        def schema = extractor.extractSchema(expectTaskCreated(TaskWithNestedBean))

        then:
        schema.children.size() == 1
    }

    private TaskInternal expectTaskCreated(final Class type, final Object... params) {
        final Class decorated = project.getServices().get(ClassGenerator).generate(type)
        TaskInternal task = (TaskInternal) AbstractTask.injectIntoNewInstance(project, "task", type, new Callable<TaskInternal>() {
            TaskInternal call() throws Exception {
                if (params.length > 0) {
                    return type.cast(decorated.constructors[0].newInstance(params))
                } else {
                    return decorated.newInstance()
                }
            }
        })
        return expectTaskCreated(task)
    }

    private TaskInternal expectTaskCreated(final TaskInternal task) {
        // We cannot just stub here as we want to return a different task each time.
        1 * delegate.createTask(args) >> task
        assert factory.createTask(args).is(task)
        return task
    }
}
