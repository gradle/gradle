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

import com.google.common.collect.ImmutableMap
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
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
        expect:
        assertHasValidationProblems(TaskWithFileInput, [
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
        expect:
        assertHasValidationProblems(CacheableTaskWithoutPathSensitivity, [
            "property 'inputFile' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE",
            "property 'inputFiles' is missing a normalization annotation, defaulting to PathSensitivity.ABSOLUTE"
        ])
    }

    static class TaskWithNestedIterable extends DefaultTask {
        @Nested
        Iterable<NestedBeanWithNonAnnotatedProperty> beans

        @Nested
        List<NestedBeanWithNonAnnotatedProperty> beanList
    }

    static class NestedBeanWithNonAnnotatedProperty {
        @Input
        String input

        String nonAnnotated
    }

    def "analyzes type arguments of Iterables"() {
        expect:
        assertHasValidationProblems(TaskWithNestedIterable, [
                "property 'beans*.nonAnnotated' is not annotated with an input or output annotation",
                "property 'beanList*.nonAnnotated' is not annotated with an input or output annotation"
        ])
    }

    static class TaskWithNestedIterableOfNamed extends DefaultTask {
        @Nested
        Iterable<NamedBean> namedBeans
    }

    static class NamedBean implements Named {
        @Input
        input

        String nonAnnotated

        @Override
        @Internal
        String getName() {
            return null
        }
    }

    def "analyzes type arguments of Iterables of Named"() {
        expect:
        assertHasValidationProblems(TaskWithNestedIterableOfNamed, [
                "property 'namedBeans.<name>.nonAnnotated' is not annotated with an input or output annotation",
        ])
    }

    static class TaskWithNestedMap extends DefaultTask {
        @Nested
        Map<String, NestedBeanWithNonAnnotatedProperty> beans

        @Nested
        ImmutableMap<Object, NestedBeanWithNonAnnotatedProperty> beanMap
    }

    def "analyzes type arguments of Maps"() {
        expect:
        assertHasValidationProblems(TaskWithNestedMap, [
                "property 'beans.<key>.nonAnnotated' is not annotated with an input or output annotation",
                "property 'beanMap.<key>.nonAnnotated' is not annotated with an input or output annotation"
        ])
    }

    static class TaskWithNestedProvider extends DefaultTask {
        @Nested
        Provider<NestedBeanWithNonAnnotatedProperty> nested
    }

    def "analyzes type arguments of Providers"() {
        expect:
        assertHasValidationProblems(TaskWithNestedProvider, [
                "property 'nested.nonAnnotated' is not annotated with an input or output annotation"
        ])
    }

    private static void assertHasValidationProblems(Class<?> taskType, List<String> expectedProblems, enableStricterValidation = false) {
        def propertyValidationAccess = new PropertyValidationAccess()
        def problems = new HashMap<String, Boolean>()
        propertyValidationAccess.collectTaskValidationProblems(taskType, problems, enableStricterValidation)

        assert problems.keySet() == validationProblems(taskType, expectedProblems)
    }

    static class TaskWithIterableInIterable extends DefaultTask {
        @Nested
        List<Set<NestedBeanWithNonAnnotatedProperty>> beans

        @Nested
        List<Map<String, List<NestedBeanWithNonAnnotatedProperty>>> nestedBeans
    }

    def "for Iterables of Iterables the right type is selected"() {
        expect:
        assertHasValidationProblems(TaskWithIterableInIterable, [
                "property 'beans**.nonAnnotated' is not annotated with an input or output annotation",
                "property 'nestedBeans*.<key>*.nonAnnotated' is not annotated with an input or output annotation",
        ])
    }

    static class AnnotatedIterable extends ArrayList<NestedBeanWithNonAnnotatedProperty> {
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
        expect:
        assertHasValidationProblems(TaskWithNestedAnnotatedIterable, [])

    }

    static class TaskWithNonAnnotatedProperty extends DefaultTask {
        FileCollection inputFiles
    }

    def "warns about non-annotated property"() {
        expect:
        assertHasValidationProblems(TaskWithNonAnnotatedProperty, [
            "property 'inputFiles' is not annotated with an input or output annotation"
        ])
    }

    def "supports recursive types"() {
        expect:
        assertHasValidationProblems(TaskWithNestedTree, [
                "property 'tree.nonAnnotated' is not annotated with an input or output annotation",
                "property 'bean.nonAnnotated' is not annotated with an input or output annotation",
                "property 'nestedBean.bean.nonAnnotated' is not annotated with an input or output annotation"
        ])
    }

    static class Tree {
        @Optional @Nested
        Tree left

        @Optional @Nested
        Tree right

        String nonAnnotated
    }

    static class BeanWithNonAnnotatedType {
        @Nested
        NestedBeanWithNonAnnotatedProperty bean
    }

    static class TaskWithNestedTree extends DefaultTask {
        @Nested Tree tree
        @Nested NestedBeanWithNonAnnotatedProperty bean
        @Nested BeanWithNonAnnotatedType nestedBean
    }

    private static Set<String> validationProblems(Class<?> task, List messages) {
        messages.collect { "Task type '${task.name}': ${it}." }*.toString() as Set
    }
}
