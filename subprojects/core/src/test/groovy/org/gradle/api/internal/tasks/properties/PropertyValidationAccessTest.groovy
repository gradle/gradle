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

package org.gradle.api.internal.tasks.properties

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import spock.lang.Specification

import java.nio.file.Path

class PropertyValidationAccessTest extends Specification {
    static class TaskWithFileInput extends DefaultTask {
        @Input
        File file

        @Input
        Path filePath

        @Input
        FileCollection fileCollection

        @Input
        FileTree fileTree
    }

    def "warns about @Input being used on File and FileCollection properties"() {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        when:
        propertyValidationAccess.collectTaskValidationProblems(TaskWithFileInput, problems)

        then:
        problems.keySet() == validationProblems(TaskWithFileInput, [
            "property 'file' has @Input annotation used on property of type $File.name",
            "property 'fileCollection' has @Input annotation used on property of type $FileCollection.name",
            "property 'filePath' has @Input annotation used on property of type $Path.name",
            "property 'fileTree' has @Input annotation used on property of type $FileTree.name"
        ])
    }

    @CacheableTask
    static class CacheableTaskWithoutPathSensitivity extends DefaultTask {
        @InputFile
        File inputFile

        @InputFiles
        FileCollection inputFiles

        @OutputFile
        File outputFile
    }

    def "warns about missing @PathSensitive annotation for @CacheableTask"() {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        when:
        propertyValidationAccess.collectTaskValidationProblems(CacheableTaskWithoutPathSensitivity, problems)

        then:
        problems.keySet() == validationProblems(CacheableTaskWithoutPathSensitivity, [
            "property 'inputFile' is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE",
            "property 'inputFiles' is missing a @PathSensitive annotation, defaulting to PathSensitivity.ABSOLUTE"
        ])
    }

    static class TaskWithIterableNested extends DefaultTask {
        @Nested
        Iterable<NestedBean> beans

        @Nested
        List<NestedBean> beanList
    }

    static class NestedBean {
        @Input
        String input

        String nonAnnotated
    }

    def "analyzes type arguments of Iterables"() {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        when:
        propertyValidationAccess.collectTaskValidationProblems(TaskWithIterableNested, problems)

        then:
        problems.keySet() == validationProblems(TaskWithIterableNested, [
                "property 'beans\$1.nonAnnotated' is not annotated with an input or output annotation",
                "property 'beanList\$1.nonAnnotated' is not annotated with an input or output annotation"
        ])
    }

    static class TaskWithIterableInIterable extends DefaultTask {
        @Nested
        List<Set<NestedBean>> beans
    }

    def "for Iterables of Iterables the right type is selected"() {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        when:
        propertyValidationAccess.collectTaskValidationProblems(TaskWithIterableInIterable, problems)

        then:
        problems.keySet() == validationProblems(TaskWithIterableInIterable, [
                "property 'beans\$1\$1.nonAnnotated' is not annotated with an input or output annotation",
        ])
    }

    static class AnnotatedIterable extends ArrayList<NestedBean> {
        @Input
        String someProperty = "annotated"

        @Override
        @Internal
        boolean isEmpty() {
            return super.isEmpty()
        }
    }

    static class TaskWithNestedAnnotatedIterable extends DefaultTask {
        @Nested
        AnnotatedIterable annotatedIterable
    }

    def "does not look at the type parameter of annotated iterable subclasses"() {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        when:
        propertyValidationAccess.collectTaskValidationProblems(TaskWithNestedAnnotatedIterable, problems)

        then:
        problems.keySet().empty

    }

    static class TaskWithNonAnnotatedProperty extends DefaultTask {
        FileCollection inputFiles
    }

    def "warns about non-annotated property"() {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        when:
        propertyValidationAccess.collectTaskValidationProblems(TaskWithNonAnnotatedProperty, problems)

        then:
        problems.keySet() == validationProblems(TaskWithNonAnnotatedProperty, [
            "property 'inputFiles' is not annotated with an input or output annotation"
        ])
    }

    private static Set<String> validationProblems(Class<?> task, List messages) {
        messages.collect { "Task type '${task.name}': ${it}." }*.toString() as Set
    }
}
