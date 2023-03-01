/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.processing

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.api.logging.Logger
import org.gradle.cache.internal.TestFileContentCacheFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.JarUtils
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector.INCREMENTAL_PROCESSOR_DECLARATION
import static org.gradle.api.internal.tasks.compile.processing.AnnotationProcessorDetector.PROCESSOR_DECLARATION

class AnnotationProcessorDetectorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    Logger logger = Mock(Logger) {
        0 * _
    }

    AnnotationProcessorDetector detector = new AnnotationProcessorDetector(new TestFileContentCacheFactory(), logger, true)

    def "detects no processors in broken jars"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << "broken"
        def cp = files(jar)

        when:
        def processors = detector.detectProcessors(cp)

        then:
        processors == [:]
        1 * logger.warn({ it.contains("no annotation processors") }, { it.message.contains("Error on ZipFile") })
    }

    def "ignores comments and empty lines in processor declaration"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents((PROCESSOR_DECLARATION): "#a comment\n\n")
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "#a comment\n\n"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp) == [:]
    }

    def "detects no processors when processor declaration is missing"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents()
        def dir = tmpDir.file("classes")
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp) == [:]
    }

    def "detects no processors when processor declaration is missing, even if Gradle metadata is present"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents((INCREMENTAL_PROCESSOR_DECLARATION): "InJar,ISOLATING")
        def dir = tmpDir.file("classes")
        dir.file(INCREMENTAL_PROCESSOR_DECLARATION) << "InDir,ISOLATING"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp) == [:]
    }

    def "detects no processors when processor declaration is not a file"() {
        given:
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION).mkdirs()
        def cp = files(dir)

        expect:
        detector.detectProcessors(cp) == [:]
    }

    def "uses UNKNOWN as the default for processors that don't provide Gradle metadata"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents((PROCESSOR_DECLARATION): "InJar")
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "InDir"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp).values().asList() == [
            new AnnotationProcessorDeclaration("InJar", IncrementalAnnotationProcessorType.UNKNOWN),
            new AnnotationProcessorDeclaration("InDir", IncrementalAnnotationProcessorType.UNKNOWN)
        ]
    }

    def "uses UNKNOWN as the default for processors with broken Gradle metadata"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents(
            (PROCESSOR_DECLARATION): "InJar",
            (INCREMENTAL_PROCESSOR_DECLARATION): "InJar       Foo     ; Bar"
        )
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "InDir"
        dir.file(INCREMENTAL_PROCESSOR_DECLARATION) << "InDir       Foo     ; Bar"
        def cp = files(jar, dir)

        when:
        def processors = detector.detectProcessors(cp).values().asList()

        then:
        processors == [
            new AnnotationProcessorDeclaration("InJar", IncrementalAnnotationProcessorType.UNKNOWN),
            new AnnotationProcessorDeclaration("InDir", IncrementalAnnotationProcessorType.UNKNOWN)
        ]
        2 * logger.warn({ it.contains("non-incremental") }, { it instanceof IndexOutOfBoundsException })
    }

    def "uses UNKNOWN as the default for processors with future types that this version does not know about"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents(
            (PROCESSOR_DECLARATION): "InJar",
            (INCREMENTAL_PROCESSOR_DECLARATION): "InJar,FOO"
        )
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "InDir"
        dir.file(INCREMENTAL_PROCESSOR_DECLARATION) << "InDir,BAR"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp).values().asList() == [
            new AnnotationProcessorDeclaration("InJar", IncrementalAnnotationProcessorType.UNKNOWN),
            new AnnotationProcessorDeclaration("InDir", IncrementalAnnotationProcessorType.UNKNOWN)
        ]
    }

    def "ignores future columns in the metadata that this version does not know about"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents(
            (PROCESSOR_DECLARATION): "InJar",
            (INCREMENTAL_PROCESSOR_DECLARATION): "InJar,AGGREGATING,Foo,Bar,Baz"
        )
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "InDir"
        dir.file(INCREMENTAL_PROCESSOR_DECLARATION) << "InDir,ISOLATING,Foo,Bar,Baz"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp).values().asList() == [
            new AnnotationProcessorDeclaration("InJar", IncrementalAnnotationProcessorType.AGGREGATING),
            new AnnotationProcessorDeclaration("InDir", IncrementalAnnotationProcessorType.ISOLATING)
        ]
    }

    def "detects incremental processors and parses their type"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents(
            (PROCESSOR_DECLARATION): "InJar",
            (INCREMENTAL_PROCESSOR_DECLARATION): "InJar,AGGREGATING"
        )
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "InDir"
        dir.file(INCREMENTAL_PROCESSOR_DECLARATION) << "InDir,ISOLATING"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp).values().asList() == [
            new AnnotationProcessorDeclaration("InJar", IncrementalAnnotationProcessorType.AGGREGATING),
            new AnnotationProcessorDeclaration("InDir", IncrementalAnnotationProcessorType.ISOLATING)
        ]
    }

    def "uses the first occurrence if the same processor is present multiple times"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents(
            (PROCESSOR_DECLARATION): "Foo",
            (INCREMENTAL_PROCESSOR_DECLARATION): "Foo,AGGREGATING"
        )
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "Foo"
        dir.file(INCREMENTAL_PROCESSOR_DECLARATION) << "Foo,ISOLATING"
        def cp = files(jar, dir)

        expect:
        detector.detectProcessors(cp).values().asList() == [
            new AnnotationProcessorDeclaration("Foo", IncrementalAnnotationProcessorType.AGGREGATING)
        ]
    }

    def "caches the analysis result for each processor"() {
        given:
        def jar = tmpDir.file("classes.jar")
        jar << JarUtils.jarWithContents((PROCESSOR_DECLARATION): "InJar")
        def dir = tmpDir.file("classes")
        dir.file(PROCESSOR_DECLARATION) << "InDir"
        def cp = files(jar, dir)

        when:
        def first = detector.detectProcessors(cp).values().asList()
        def second = detector.detectProcessors(cp).values().asList()

        then:
        for (int i = 0; i < first.size(); i++) {
            assert first.get(i).is(second.get(i))
        }
    }

    FileCollection files(File... files) {
        TestFiles.fixed(files)
    }
}
