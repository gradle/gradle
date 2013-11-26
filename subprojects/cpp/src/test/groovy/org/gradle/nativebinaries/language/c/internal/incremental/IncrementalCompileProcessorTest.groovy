/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.c.internal.incremental
import org.gradle.CacheUsage
import org.gradle.api.internal.hash.DefaultHasher
import org.gradle.cache.internal.FileLockManager
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.messaging.serialize.DefaultSerializer
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.junit.Rule
import spock.lang.Specification

class IncrementalCompileProcessorTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def cacheDir = tmpDir.createDir("cache")
    def dependencyParser = Mock(SourceDependencyParser)
    def cacheFactory = new InMemoryCacheFactory()
    def hasher = new DefaultHasher()
    def stateCache = cacheFactory.openIndexedCache(cacheDir, CacheUsage.ON, null, null, LockOptionsBuilder.mode(FileLockManager.LockMode.None), new DefaultSerializer<FileState>())
    def listCache = cacheFactory.openIndexedCache(cacheDir, CacheUsage.ON, null, null, LockOptionsBuilder.mode(FileLockManager.LockMode.None), new DefaultSerializer<List<File>>())
    def incrementalCompileProcessor = new IncrementalCompileProcessor(stateCache, listCache, dependencyParser, hasher)

    def source1 = sourceFile("source1")
    def source2 = sourceFile("source2")
    def dep1 = sourceFile("dep1")
    def dep2 = sourceFile("dep2")
    def dep3 = sourceFile("dep3")
    def dep4 = sourceFile("dep4")
    def sourceFiles

    def initialFiles() {
        // S1 - D1 \
        //    \ D2  \
        //           D3
        // S2 ------/
        //    \ D4

        1 * dependencyParser.parseDependencies(source1) >> [dep1, dep2]
        1 * dependencyParser.parseDependencies(source2) >> [dep3, dep4]
        1 * dependencyParser.parseDependencies(dep1) >> [dep3]
        1 * dependencyParser.parseDependencies(dep2) >> []
        1 * dependencyParser.parseDependencies(dep3) >> []
        1 * dependencyParser.parseDependencies(dep4) >> []

        sourceFiles = [source1, source2]
        with (state) {
            assert recompile == [source1, source2]
            assert removed == []
        }
    }

    def "detects unchanged source files"() {
        given:
        initialFiles()

        expect:
        with (state) {
            recompile == []
            removed == []
        }
    }

    def "detects new source files"() {
        given:
        initialFiles()

        when:
        def file3 = sourceFile("file3")
        sourceFiles << file3
        1 * dependencyParser.parseDependencies(file3) >> []

        then:
        with (state) {
            recompile == [file3]
            removed == []
        }
    }

    def "detects removed source file"() {
        given:
        initialFiles()

        when:
        sourceFiles.remove(source2)

        then:
        with (state) {
            recompile == []
            removed == [source2]
        }

        and:
        cached dep3
        uncached dep4
    }

    def "detects removed source file with multiple dependencies"() {
        given:
        initialFiles()

        when:
        sourceFiles.remove(source1)

        then:
        with (state) {
            recompile == []
            removed == [source1]
        }

        and:
        uncached dep1, dep2
        cached dep3, dep4
    }

    def "detects source file changed"() {
        given:
        initialFiles()

        when:
        source2 << "More text"
        1 * dependencyParser.parseDependencies(source2) >> [dep3, dep4]

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }

        and:
        cached dep3, dep4
    }

    def "detects dependency file changed"() {
        given:
        initialFiles()

        when:
        dep4 << "More text"
        1 * dependencyParser.parseDependencies(dep4) >> []

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }
    }

    def "detects shared dependency file changed"() {
        given:
        initialFiles()

        when:
        dep3 << "More text"
        1 * dependencyParser.parseDependencies(dep3) >> []

        then:
        with (state) {
            recompile == [source1, source2]
            removed == []
        }
    }

    def "detects source file change with new dependencies"() {
        given:
        initialFiles()

        when:
        source2 << "More text"
        def dep5 = sourceFile("dep5")
        1 * dependencyParser.parseDependencies(source2) >> [dep3, dep4, dep5]
        1 * dependencyParser.parseDependencies(dep5) >> []

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }

        when:
        dep5 << "changed"
        1 * dependencyParser.parseDependencies(dep5) >> []

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }
    }

    def "detects dependency file change with new dependencies"() {
        given:
        initialFiles()

        when:
        dep4 << "More text"
        def dep5 = sourceFile("dep5")
        1 * dependencyParser.parseDependencies(dep4) >> [dep5]
        1 * dependencyParser.parseDependencies(dep5) >> []

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }

        when:
        dep5 << "changed"
        1 * dependencyParser.parseDependencies(dep5) >> []

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }
    }

    def "detects dependency file change adding dependency cycle"() {
        given:
        initialFiles()

        when:
        dep3 << "More text"
        1 * dependencyParser.parseDependencies(dep3) >> [dep1]

        then:
        with (state) {
            recompile == [source1, source2]
            removed == []
        }
    }

    def "detects shared dependency file changed with new dependency"() {
        given:
        initialFiles()

        when:
        dep3 << "More text"
        def dep5 = sourceFile("dep5")
        1 * dependencyParser.parseDependencies(dep3) >> [dep5]
        1 * dependencyParser.parseDependencies(dep5) >> []

        then:
        with (state) {
            recompile == [source1, source2]
            removed == []
        }
        when:
        dep5 << "changed"
        1 * dependencyParser.parseDependencies(dep5) >> []

        then:
        with (state) {
            recompile == [source1, source2]
            removed == []
        }
    }

    def "detects changed dependency with new source file including that dependency"() {
        given:
        initialFiles()

        when:
        dep4 << "change"
        1 * dependencyParser.parseDependencies(dep4) >> []

        def file3 = sourceFile("file3")
        sourceFiles = [file3, source1, source2]
        1 * dependencyParser.parseDependencies(file3) >> [dep4]

        then:
        with (state) {
            recompile == [file3, source2]
            removed == []
        }
    }

    def "detects source file removed then readded"() {
        given:
        initialFiles()

        when:
        sourceFiles.remove(source2)

        then:
        with (state) {
            recompile == []
            removed == [source2]
        }

        when:
        sourceFiles.add(source2)
        1 * dependencyParser.parseDependencies(source2) >> []

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }
    }

    def "handles source file that is also a dependency"() {
        given:
        initialFiles()

        and:
        dep2 << "changed"
        1 * dependencyParser.parseDependencies(dep2) >> [source2]
        with (state) {
            recompile == [source1]
        }

        when:
        dep4 << "changed"
        1 * dependencyParser.parseDependencies(dep4) >> []

        then:
        with (state) {
            recompile == [source1, source2]
        }
    }

    def "reports source file changed to dependency as removed"() {
        given:
        initialFiles()

        when:
        dep2 << "added dep"
        1 * dependencyParser.parseDependencies(dep2) >> [source2]

        and:
        sourceFiles.remove(source2)

        then:
        with (state) {
            recompile == [source1]
            removed == [source2]
        }

        when:
        sourceFiles.add(source2)

        then:
        with (state) {
            recompile == [source2]
            removed == []
        }
    }

    def getState() {
        incrementalCompileProcessor.processSourceFiles(sourceFiles)
    }

    def cached(File... files) {
        files.each {
            assert stateCache.get(it) != null
        }
        true
    }

    def uncached(File... files) {
        files.each {
            assert stateCache.get(it) == null
        }
        true
    }

    def sourceFile(def name) {
        tmpDir.createFile(name) << "initial text"
    }
}
