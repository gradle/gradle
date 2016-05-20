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
import org.gradle.api.tasks.*
import spock.lang.Specification

import javax.inject.Inject

class DefaultTaskClassInfoStoreTest extends Specification {
    def taskClassInfoStore = new DefaultTaskClassInfoStore()

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
    }

    def "can get annotated properties of simple task"() {
        def info = taskClassInfoStore.getTaskClassInfo(SimpleTask)

        expect:
        !info.incremental
        info.validator.validatedProperties*.name as Set == ["inputString", "inputFile", "inputDirectory", "inputFiles", "outputFile", "outputFiles", "outputDirectory", "outputDirectories", "injectedService"] as Set
        info.nonAnnotatedPropertyNames.empty
    }

    private static class BaseTask extends DefaultTask {
        @Input String baseValue
        @Input String superclassValue
        String nonAnnotatedBaseValue
    }

    private static class OverridingTask extends BaseTask {
        @Override
        String getSuperclassValue() {
            return super.getSuperclassValue()
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
        info.validator.validatedProperties*.name as Set == ["baseValue", "superclassValue", "nonAnnotatedBaseValue"] as Set
        info.nonAnnotatedPropertyNames.empty
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

    def "implemented properties do not inherit interface annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(InterfaceImplementingTask)

        expect:
        !info.incremental
        info.validator.validatedProperties*.name as Set == [] as Set
        info.nonAnnotatedPropertyNames == ["interfaceValue"] as Set
    }

    private static class NonAnnotatedTask extends DefaultTask {
        File inputFile

        @SuppressWarnings("GrMethodMayBeStatic")
        String getValue() {
            "test"
        }
    }

    def "detects properties without annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(NonAnnotatedTask)

        expect:
        !info.incremental
        info.validator.validatedProperties*.name as Set == [] as Set
        info.validator.nonAnnotatedPropertyNames == ["inputFile", "value"] as Set
    }

    def "class infos are cached"() {
        def info = taskClassInfoStore.getTaskClassInfo(SimpleTask)
        expect:
        info == taskClassInfoStore.getTaskClassInfo(SimpleTask)
    }
}
