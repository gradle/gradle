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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject

class DefaultTaskClassInfoStoreTest extends Specification {
    def taskClassInfoStore = new DefaultTaskClassInfoStore(new DefaultTaskClassValidatorExtractor())

    @SuppressWarnings("GrDeprecatedAPIUsage")
    private static class SimpleTask extends DefaultTask {
        @Input String inputString
        @InputFile File inputFile
        @InputDirectory File inputDirectory
        @InputFiles File inputFiles
        @OutputFile File outputFile
        @OutputFiles Set<File> outputFiles
        @OutputDirectory File outputDirectory
        @OutputDirectories Set<File> outputDirectories
        @Inject Object injectedService
        @Internal Object internal
        @Console boolean console
    }

    def "can get annotated properties of simple task"() {
        def info = taskClassInfoStore.getTaskClassInfo(SimpleTask)

        expect:
        !info.incremental
        !info.cacheable
        info.validator.annotatedProperties*.name.sort() == ["inputDirectory", "inputFile", "inputFiles", "inputString", "outputDirectories", "outputDirectory", "outputFile", "outputFiles"]
    }

    @CacheableTask
    private static class MyCacheableTask extends DefaultTask {}

    def "cacheable tasks are detected"() {
        expect:
        taskClassInfoStore.getTaskClassInfo(MyCacheableTask).cacheable
    }

    private static class MyNonCacheableTask extends MyCacheableTask {}

    def "cacheability is not inherited"() {
        expect:
        !taskClassInfoStore.getTaskClassInfo(MyNonCacheableTask).cacheable
    }

    private static class BaseTask extends DefaultTask {
        @Input String baseValue
        @Input String superclassValue
        @Input String superclassValueWithDuplicateAnnotation
        String nonAnnotatedBaseValue
    }

    private static class OverridingTask extends BaseTask {
        @Override
        String getSuperclassValue() {
            return super.getSuperclassValue()
        }

        @Input @Override
        String getSuperclassValueWithDuplicateAnnotation() {
            return super.getSuperclassValueWithDuplicateAnnotation()
        }

        @Input @Override
        String getNonAnnotatedBaseValue() {
            return super.getNonAnnotatedBaseValue()
        }
    }

    def "overridden properties inherit super-class annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(OverridingTask)

        expect:
        !info.incremental
        info.validator.annotatedProperties*.name.sort() == ["baseValue", "nonAnnotatedBaseValue", "superclassValue", "superclassValueWithDuplicateAnnotation"]
    }

    private interface TaskSpec {
        @Input
        String getInterfaceValue()
    }

    private static class InterfaceImplementingTask extends DefaultTask implements TaskSpec {
        @Override
        String getInterfaceValue() {
            "value"
        }
    }

    def "implemented properties inherit interface annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(InterfaceImplementingTask)

        expect:
        !info.incremental
        info.validator.annotatedProperties*.name.sort() == ["interfaceValue"]
    }

    private static class NonAnnotatedTask extends DefaultTask {
        File inputFile

        @SuppressWarnings("GrMethodMayBeStatic")
        String getValue() {
            "test"
        }
    }

    def "class infos are cached"() {
        def info = taskClassInfoStore.getTaskClassInfo(SimpleTask)
        expect:
        info == taskClassInfoStore.getTaskClassInfo(SimpleTask)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class IsGetterTask extends DefaultTask {
        @Input
        private boolean feature1
        private boolean feature2

        boolean isFeature1() {
            return feature1
        }
        void setFeature1(boolean enabled) {
            this.feature1 = enabled
        }
        boolean isFeature2() {
            return feature2
        }
        void setFeature2(boolean enabled) {
            this.feature2 = enabled
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2115")
    def "annotation on private filed is recognized for is-getter"() {
        def info = taskClassInfoStore.getTaskClassInfo(IsGetterTask)
        expect:
        info.validator.annotatedProperties*.name as List == ["feature1"]
    }
}
