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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDeclaration
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector
import org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorPathFactory
import org.gradle.api.internal.tasks.compile.processing.DefaultProcessorPath
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class AnnotationProcessorPathFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    AnnotationProcessorDetector detector = Mock(AnnotationProcessorDetector)
    CompileOptions options = new CompileOptions(Mock(ObjectFactory))
    AnnotationProcessorPathFactory factory = new AnnotationProcessorPathFactory(TestFiles.fileCollectionFactory(), detector)

    def "uses path defined on Java compile options, as a FileCollection"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = ["-processorpath", "ignore-me"]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp) == procPath
    }

    def "uses path defined on Java compile options, as a Configuration"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = Mock(Configuration) {
            isEmpty() >> false
            getFiles() >> procPath.files
        }
        options.compilerArgs = ["-processorpath", "ignore-me"]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).files == procPath.files
    }

    @Unroll
    def "uses empty path when defined on Java compile options as a FileCollection (where compilerArgs #scenario)"() {
        def cp = files("lib.jar")
        def procPath = files()

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = compilerArgs

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).empty

        where:
        scenario             | compilerArgs
        'is empty'           | []
        'has -processorpath' | ["-processorpath", "ignore-me"]
    }

    @Unroll
    def "uses path defined using -processorpath compiler arg (where options.annotationProcessorPath is #scenario)"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar", "proc-lib.jar")

        given:
        options.annotationProcessorPath = annotationProcessorPath
        options.compilerArgs = ["-processorpath", procPath.asPath]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).files == procPath.files

        where:
        scenario                          | annotationProcessorPath
        'null'                            | null
        'an empty default processor path' | Mock(DefaultProcessorPath) { isEmpty() >> true }
    }

    @Unroll
    def "fails when -processorpath is the last compiler arg (where options.annotationProcessorPath is #scenario)"() {
        def cp = files("lib.jar")

        given:
        options.compilerArgs = ["-Xthing", "-processorpath"]

        when:
        options.annotationProcessorPath = annotationProcessorPath
        factory.getEffectiveAnnotationProcessorClasspath(options, cp)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == 'No path provided for compiler argument -processorpath in requested compiler args: -Xthing -processorpath'

        where:
        scenario                          | annotationProcessorPath
        'null'                            | null
        'an empty default processor path' | Mock(DefaultProcessorPath) { isEmpty() >> true }
    }

    def "uses empty path when processing disabled"() {
        def cp = files("lib.jar")
        def procPath = files("processor.jar")

        given:
        options.annotationProcessorPath = procPath
        options.compilerArgs = ["-processorpath", "ignore-me", "-proc:none"]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).empty
    }

    @Issue("gradle/gradle#1471")
    @Unroll
    def "uses compile classpath when -processor is found in compile options and no processor path is defined (where options.annotationProcessorPath is #scenario)"() {
        given:
        def dir = tmpDir.file("classes-dir")
        dir.file("com/foo/Processor.class").createFile()
        def cp = files(dir)

        when:
        options.compilerArgs = ['-processor', 'com.foo.Processor']

        then:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).files == cp.files

        where:
        scenario                 | annotationProcessorPath
        'null'                   | null
        'an empty configuration' | Mock(Configuration) { isEmpty() >> true }
    }

    @Issue("gradle/gradle#1471")
    @Unroll
    def "uses processorpath when -processor is found in compile options and explicit processor path is defined (where options.annotationProcessorPath is #scenario)"() {
        given:
        def dir = tmpDir.file("classes-dir")
        dir.file("com/foo/Processor.class").createFile()
        def cp = files(dir)
        def procPath = files("processor.jar", "proc-lib.jar")

        when:
        options.compilerArgs = ['-processor', 'com.foo.Processor']
        options.compilerArgs = ["-processorpath", procPath.asPath]

        then:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).files == procPath.files

        where:
        scenario                 | annotationProcessorPath
        'null'                   | null
        'an empty configuration' | Mock(Configuration) { isEmpty() >> true }
    }

    @Issue("gradle/gradle#1471")
    def "fails when -processor is the last compiler arg"() {
        def cp = files("lib.jar")

        given:
        options.compilerArgs = ["-Xthing", "-processor"]

        when:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp)

        then:
        def e = thrown(InvalidUserDataException)
        e.message == 'No processor specified for compiler argument -processor in requested compiler args: -Xthing -processor'
    }

    @Unroll
    def "uses compile classpath when it contains processors (where options.annotationProcessorPath is #scenario)"() {
        given:
        def jar = tmpDir.file("classes.jar")
        def dir = tmpDir.file("classes-dir")
        def cp = files(jar, dir)
        detector.detectProcessors(cp) >> ["Foo" : Mock(AnnotationProcessorDeclaration)]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).files == cp.files

        where:
        scenario                 | annotationProcessorPath
        'null'                   | null
        'an empty configuration' | Mock(Configuration) { isEmpty() >> true }
    }

    @Unroll
    def "uses empty path when compile classpath does not include any processors (where options.annotationProcessorPath is #scenario)"() {
        given:
        def dir = tmpDir.file("classes-dir")
        def jar = tmpDir.file("classes.jar")
        def cp = files(dir, jar)
        detector.detectProcessors(cp) >> [:]

        expect:
        factory.getEffectiveAnnotationProcessorClasspath(options, cp).empty

        where:
        scenario                 | annotationProcessorPath
        'null'                   | null
        'an empty configuration' | Mock(Configuration) { isEmpty() >> true }
    }

    FileCollection files(String... paths) {
        new SimpleFileCollection(paths.collect { tmpDir.file(it).createFile() })
    }

    FileCollection files(File... files) {
        new SimpleFileCollection(files)
    }
}
