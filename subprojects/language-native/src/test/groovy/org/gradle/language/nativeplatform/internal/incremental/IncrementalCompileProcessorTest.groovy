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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.gradle.api.internal.hash.FileHasher
import org.gradle.cache.PersistentStateCache
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultIncludeDirectives
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class IncrementalCompileProcessorTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def includesParser = Mock(SourceIncludesParser)
    def dependencyParser = Mock(SourceIncludesResolver)
    def hasher = Stub(FileHasher)
    def stateCache = new DummyPersistentStateCache()
    def incrementalCompileProcessor = new IncrementalCompileProcessor(stateCache, dependencyParser, includesParser, hasher)

    def source1 = sourceFile("source1")
    def source2 = sourceFile("source2")
    def dep1 = sourceFile("dep1")
    def dep2 = sourceFile("dep2")
    def dep3 = sourceFile("dep3")
    def dep4 = sourceFile("dep4")
    def sourceFiles

    Map<TestFile, List<ResolvedInclude>> graph = [:]
    List<TestFile> modified = []

    def setup() {
        hasher.hash(_) >> { File file ->
            Files.asByteSource(file).hash(Hashing.sha1())
        }

        // S1 - D1 \
        //    \ D2  \
        //           D3
        // S2 ------/
        //    \ D4

        graph[source1] = deps(dep1, dep2)
        graph[source2] = deps(dep3, dep4)
        graph[dep1] = deps(dep3)
        graph[dep2] = []
        graph[dep3] = []
        graph[dep4] = []
    }

    def initialFiles() {

        graph.keySet().each { TestFile sourceFile ->
            parse(sourceFile)
            resolve(sourceFile)
        }

        sourceFiles = [source1, source2]
        with (state) {
            assert recompile == [source1, source2]
            assert removed == []
        }
    }

    def parse(TestFile sourceFile) {
        final Set<ResolvedInclude> deps = graph[sourceFile]
        IncludeDirectives includes = includes(deps)
        1 * includesParser.parseIncludes(sourceFile) >> includes
    }

    def resolve(TestFile sourceFile) {
        Set<ResolvedInclude> deps = graph[sourceFile]
        IncludeDirectives includes = includes(deps)
        1 * dependencyParser.resolveIncludes(sourceFile, includes) >> resolveDeps(deps)
    }

    private static IncludeDirectives includes(Set<ResolvedInclude> deps) {
        return new DefaultIncludeDirectives(deps.collect { '<' + it.file.name + '>' })
    }

    def added(TestFile sourceFile) {
        modified << sourceFile
        graph[sourceFile] = []
    }

    def sourceAdded(TestFile sourceFile, def deps = []) {
        sourceFiles << sourceFile
        modified << sourceFile
        graph[sourceFile] = deps
    }

    def modified(TestFile sourceFile, def deps = null) {
        modified << sourceFile
        sourceFile << "More text"
        if (deps != null) {
            graph[sourceFile] = deps
        }
    }

    def sourceRemoved(TestFile sourceFile) {
        sourceFiles.remove(sourceFile)
        graph.remove(sourceFile)
    }

    def dependencyRemoved(TestFile sourceFile) {
        graph.remove(sourceFile)
        sourceFile.delete()
    }

    def "detects unchanged source files"() {
        given:
        initialFiles()

        expect:
        checkCompile recompiled: [], removed: []
    }

    def "detects new source files"() {
        given:
        initialFiles()

        when:
        def file3 = sourceFile("file3")
        sourceAdded(file3)

        then:
        checkCompile recompiled: [file3], removed: []
    }

    def "detects removed source file"() {
        given:
        initialFiles()

        when:
        sourceRemoved(source2)
        graph.remove(dep4)

        then:
        checkCompile recompiled: [], removed: [source2]
    }

    def "detects removed source file with multiple dependencies"() {
        given:
        initialFiles()

        when:
        sourceRemoved(source1)
        graph.remove(dep1)
        graph.remove(dep2)

        then:
        checkCompile recompiled: [], removed: [source1]
    }

    def "detects source file changed"() {
        given:
        initialFiles()

        when:
        modified(source2)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "detects dependency file changed"() {
        given:
        initialFiles()

        when:
        modified(dep4)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "detects dependency file removed"() {
        given:
        initialFiles()

        when:
        dependencyRemoved(dep4)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "detects shared dependency file changed"() {
        given:
        initialFiles()

        when:
        modified(dep3)

        then:
        checkCompile recompiled: [source1, source2], removed: []
    }

    def "detects source file change with new dependencies"() {
        given:
        initialFiles()

        when:
        def dep5 = sourceFile("dep5")
        added(dep5)
        modified(source2, deps(dep3, dep4, dep5))

        then:
        checkCompile recompiled: [source2], removed: []

        when:
        modified(dep5)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "detects unchanged source file change with different resolved dependencies"() {
        given:
        initialFiles()

        when:
        def dep5 = sourceFile("dep5")
        graph[dep5] = []

        resolve(source1)
        resolve(dep1)
        resolve(dep2)
        resolve(dep3)
        parse(dep5)
        resolve(dep5)

        1 * dependencyParser.resolveIncludes(source2, includes(deps(dep3, dep4))) >> resolveDeps(deps(dep3, dep5))

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
        def dep5 = sourceFile("dep5")
        modified(dep4, deps(dep5))
        added(dep5)

        then:
        checkCompile recompiled: [source2], removed: []

        when:
        modified(dep5)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "detects dependency file change adding dependency cycle"() {
        given:
        initialFiles()

        when:
        modified(dep3, deps(dep1))

        then:
        checkCompile recompiled: [source1, source2], removed: []
    }

    def "detects shared dependency file changed with new dependency"() {
        given:
        initialFiles()

        when:
        def dep5 = sourceFile("dep5")
        modified(dep3, deps(dep5))
        added(dep5)

        then:
        checkCompile recompiled: [source1, source2], removed: []

        when:
        modified(dep5)

        then:
        checkCompile recompiled: [source1, source2], removed: []
    }

    def "detects changed dependency with new source file including that dependency"() {
        given:
        initialFiles()

        when:
        def file3 = sourceFile("file3")
        sourceAdded(file3, deps(dep4))
        modified(dep4)

        then:
        checkCompile recompiled: [source2, file3], removed: []
    }

    def "detects source file removed then readded"() {
        given:
        initialFiles()

        when:
        sourceRemoved(source2)
        graph.remove(dep4)

        then:
        checkCompile recompiled: [], removed: [source2]

        when:
        sourceAdded(source2, [])

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "handles source file that is also a dependency"() {
        given:
        initialFiles()

        when:
        modified(dep2, deps(source2))

        then:
        checkCompile recompiled: [source1], removed: []

        when:
        modified(dep4)

        then:
        checkCompile recompiled: [source1, source2], removed: []
    }

    def "reports source file changed to dependency as removed"() {
        given:
        initialFiles()

        when:
        modified(dep2, deps(source2))
        sourceFiles.remove(source2)

        then:
        checkCompile recompiled: [source1], removed: [source2]

        when:
        sourceFiles.add(source2)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def checkCompile(Map<String, List<File>> args) {
        parseAndResolve()
        with (state) {
            assert recompile == args['recompiled']
            assert removed == args['removed']
        }
        return true
    }

    def parseAndResolve() {
        modified.each {
            parse(it)
        }
        modified.clear()
        graph.keySet().each { TestFile sourceFile ->
            resolve(sourceFile)
        }
        true
    }

    def getState() {
        def incrementalState = incrementalCompileProcessor.processSourceFiles(sourceFiles)
        stateCache.set(incrementalState.finalState)
        return incrementalState
    }

    def sourceFile(def name) {
        tmpDir.createFile(name) << "initial text"
    }

    Set<ResolvedInclude> deps(File... dep) {
        dep.collect {new ResolvedInclude(it.name, it)} as Set
    }

    SourceIncludesResolver.ResolvedSourceIncludes resolveDeps(Set<ResolvedInclude> deps) {
        new SourceIncludesResolver.ResolvedSourceIncludes() {
            @Override
            Set<ResolvedInclude> getResolvedIncludes() {
                return deps
            }

            @Override
            Set<File> getCheckedLocations() {
                return [] as Set
            }
        }
    }

    class DummyPersistentStateCache implements PersistentStateCache<CompilationState> {
        private CompilationState compilationState

        CompilationState get() {
            return compilationState
        }

        void set(CompilationState newValue) {
            this.compilationState = newValue
        }

        void update(PersistentStateCache.UpdateAction<CompilationState> updateAction) {

        }
    }
}
