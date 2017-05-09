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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.cache.TestFileContentCacheFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JarUtils
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

class AnnotationProcessorDetectorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def detector = new AnnotationProcessorDetector(TestFiles.fileCollectionFactory(), new TestFileContentCacheFactory())
    def options = new CompileOptions()

    def "uses path defined on Java compile options"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = ["-processorpath", "ignore-me"]

        expect:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp) == procPath
    }

    def "uses path defined using -processorpath compiler arg"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar", "proc-lib.jar")

        given:
        options.compilerArgs = ["-processorpath", procPath.asPath]

        expect:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).files == procPath.files
    }

    def "fails when -processorpath is the last compiler arg"() {
        def cp = files("lib.jar")

        given:
        options.compilerArgs = ["-Xthing", "-processorpath"]

        when:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == 'No path provided for compiler argument -processorpath in requested compiler args: -Xthing -processorpath'
    }

    def "uses empty path when processing disabled"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = ["-processorpath", "ignore-me", "-proc:none"]

        expect:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).empty
    }

    def "uses compile classpath when directory contains service resource"() {
        given:
        def dir = tmpDir.file("classes-dir")
        dir.file("META-INF/services/javax.annotation.processing.Processor").createFile()
        def cp = files(dir)

        expect:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).files == cp.files
    }

    @Issue("gradle/gradle#1471")
    def "uses compile classpath when -processor is found in compile options and no processor path is defined"() {
        given:
        def dir = tmpDir.file("classes-dir")
        dir.file("com/foo/Processor.class").createFile()
        def cp = files(dir)

        when:
        options.compilerArgs = ['-processor', 'com.foo.Processor']

        then:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).files == cp.files
    }

    @Issue("gradle/gradle#1471")
    def "uses processorpath when -processor is found in compile options and explicit processor path is defined"() {
        given:
        def dir = tmpDir.file("classes-dir")
        dir.file("com/foo/Processor.class").createFile()
        def cp = files(dir)
        def procPath = files("processor.jar", "proc-lib.jar")

        when:
        options.compilerArgs = ['-processor', 'com.foo.Processor']
        options.compilerArgs = ["-processorpath", procPath.asPath]

        then:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).files == procPath.files
    }

    @Issue("gradle/gradle#1471")
    def "fails when -processor is the last compiler arg"() {
        def cp = files("lib.jar")

        given:
        options.compilerArgs = ["-Xthing", "-processor"]

        when:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == 'No processor specified for compiler argument -processor in requested compiler args: -Xthing -processor'
    }

    def "uses compile classpath when jar contains service resource"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents("META-INF/services/javax.annotation.processing.Processor": "thing")
        def cp = files(jar)

        expect:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).files == cp.files
    }

    def "uses empty path when compile classpath does not include any processors"() {
        given:
        def dir = tmpDir.file("classes-dir")
        dir.file("Thing.class").createFile()
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents("Other.class": "other")
        def cp = files(dir, jar)

        expect:
        detector.getEffectiveAnnotationProcessorClasspath(options, cp).empty
    }

    FileCollection files(String... paths) {
        new SimpleFileCollection(paths.collect { tmpDir.file(it).createFile() })
    }

    FileCollection files(File... files) {
        new SimpleFileCollection(files)
    }
}
