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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorPathFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class AnnotationProcessorPathFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    CompileOptions options = new CompileOptions(Stub(ProjectLayout), Mock(ObjectFactory))
    AnnotationProcessorPathFactory factory = new AnnotationProcessorPathFactory(TestFiles.fileCollectionFactory())

    def "uses path defined on Java compile options, as a FileCollection"() {
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = ["-processorpath", "ignore-me"]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options) == procPath
    }

    def "uses path defined on Java compile options, as a Configuration"() {
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = Mock(Configuration) {
            isEmpty() >> false
            getFiles() >> procPath.files
        }
        options.compilerArgs = ["-processorpath", "ignore-me"]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options).files == procPath.files
    }

    @Unroll
    def "uses empty path when defined on Java compile options as a FileCollection (where compilerArgs #scenario)"() {
        def procPath = files()

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = compilerArgs

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options).empty

        where:
        scenario             | compilerArgs
        'is empty'           | []
        'has -processorpath' | ["-processorpath", "ignore-me"]
    }

    def "ignores path defined using -processorpath compiler arg"() {
        def procPath = files("processor.jar", "proc-lib.jar")

        given:
        options.annotationProcessorPath = null
        options.compilerArgs = ["-processorpath", procPath.asPath]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options).empty
    }

    def "uses empty path when processing disabled"() {
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = ["-processorpath", "ignore-me", "-proc:none"]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options).empty
    }

    @Unroll
    def "uses empty path when options.annotationProcessorPath is #scenario"() {
        when:
        options.annotationProcessorPath = annotationProcessorPath

        then:
        factory.getEffectiveAnnotationProcessorClasspath(options).empty

        where:
        scenario                 | annotationProcessorPath
        'null'                   | null
        'an empty configuration' | Mock(Configuration) { isEmpty() >> true }
    }

    FileCollection files(String... paths) {
        ImmutableFileCollection.of(paths.collect { tmpDir.file(it).createFile() } as File[])
    }

    FileCollection files(File... files) {
        ImmutableFileCollection.of(files)
    }
}
