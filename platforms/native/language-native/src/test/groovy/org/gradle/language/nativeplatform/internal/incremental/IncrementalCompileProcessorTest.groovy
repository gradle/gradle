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

import org.gradle.api.internal.file.TestFiles
import org.gradle.cache.ObjectHolder
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.TestIncludeParser
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable

@UsesNativeServices
class IncrementalCompileProcessorTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def includesParser = Mock(SourceIncludesParser)
    def dependencyResolver = new DummyResolver()
    def virtualFileSystem = TestFiles.virtualFileSystem()
    def fileSystemAccess = TestFiles.fileSystemAccess(virtualFileSystem)
    def stateCache = new DummyObjectHolder()
    def incrementalCompileProcessor = new IncrementalCompileProcessor(stateCache, new IncrementalCompileFilesFactory(IncludeDirectives.EMPTY, includesParser, dependencyResolver, fileSystemAccess), new TestBuildOperationExecutor())

    def source1 = sourceFile("source1")
    def source2 = sourceFile("source2")
    def dep1 = sourceFile("dep1")
    def dep2 = sourceFile("dep2")
    def dep3 = sourceFile("dep3")
    def dep4 = sourceFile("dep4")
    def sourceFiles

    Map<TestFile, List<File>> graph = [:]
    List<TestFile> modifiedFiles = []

    def setup() {
        // S1 - D1 \
        //    \ D2  \
        //           D3
        // S2 ------/
        //    \ D4

        graph[source1] = [dep1, dep2]
        graph[source2] = [dep3, dep4]
        graph[dep1] = [dep3]
        graph[dep2] = []
        graph[dep3] = []
        graph[dep4] = []
    }

    def initialFiles() {
        graph.keySet().each { TestFile sourceFile ->
            parse(sourceFile)
        }

        sourceFiles = [source1, source2]
        with(state) {
            assert recompile == [source1, source2]
            assert removed == []
        }
    }

    def parse(TestFile sourceFile) {
        _ * includesParser.parseIncludes(sourceFile) >> {
            def deps = graph[sourceFile]
            return includes(deps)
        }
    }

    private static IncludeDirectives includes(Collection<File> deps) {
        return TestIncludeParser.systemIncludes(deps.collect { it.name })
    }

    def added(TestFile sourceFile) {
        virtualFileSystem.invalidateAll()
        modifiedFiles << sourceFile
        graph[sourceFile] = []
    }

    def sourceAdded(TestFile sourceFile, List<File> deps = []) {
        virtualFileSystem.invalidateAll()
        sourceFiles << sourceFile
        modifiedFiles << sourceFile
        graph[sourceFile] = deps
    }

    def modified(TestFile sourceFile, List<File> deps = null) {
        virtualFileSystem.invalidateAll()
        modifiedFiles << sourceFile
        sourceFile << "More text"
        if (deps != null) {
            graph[sourceFile] = deps
        }
    }

    def sourceRemoved(TestFile sourceFile) {
        virtualFileSystem.invalidateAll()
        sourceFiles.remove(sourceFile)
        graph.remove(sourceFile)
    }

    def dependencyRemoved(TestFile sourceFile) {
        virtualFileSystem.invalidateAll()
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
        modified(source2, [dep3, dep4, dep5])

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
        parse(dep5)
        dependencyResolver.resolveAs("dep4", dep5)
        graph[source2] = [dep3, dep5]

        then:
        with(state) {
            recompile == [source2]
            removed == []
        }
    }

    def "detects dependency file change with new dependencies"() {
        given:
        initialFiles()

        when:
        def dep5 = sourceFile("dep5")
        modified(dep4, [dep5])
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
        modified(dep3, [dep1])

        then:
        checkCompile recompiled: [source1, source2], removed: []
    }

    def "detects shared dependency file changed with new dependency"() {
        given:
        initialFiles()

        when:
        def dep5 = sourceFile("dep5")
        modified(dep3, [dep5])
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
        sourceAdded(file3, [dep4])
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
        modified(dep2, [source2])

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
        modified(dep2, [source2])
        sourceFiles.remove(source2)

        then:
        checkCompile recompiled: [source1], removed: [source2]

        when:
        sourceFiles.add(source2)

        then:
        checkCompile recompiled: [source2], removed: []
    }

    def "discovers if unresolved includes have been used"() {
        given:
        parse(source1)
        parse(dep1)
        parse(dep2)
        parse(dep3)
        dependencyResolver.unresolved(source1)

        when:
        def result = incrementalCompileProcessor.processSourceFiles([source1])

        then:
        result.unresolvedHeaders
    }

    def checkCompile(Map<String, List<File>> args) {
        parseAndResolve()
        with(state) {
            assert recompile == args['recompiled']
            assert removed == args['removed']
        }
        return true
    }

    def parseAndResolve() {
        modifiedFiles.each {
            parse(it)
        }
        modifiedFiles.clear()
        true
    }

    def getState() {
        def incrementalState = incrementalCompileProcessor.processSourceFiles(sourceFiles)
        stateCache.set(incrementalState.finalState)
        return incrementalState
    }

    def sourceFile(def name) {
        tmpDir.createFile(name) << name
    }

    class DummyObjectHolder implements ObjectHolder<CompilationState> {
        private CompilationState compilationState

        CompilationState get() {
            return compilationState
        }

        void set(CompilationState newValue) {
            this.compilationState = newValue
        }

        CompilationState update(ObjectHolder.UpdateAction<CompilationState> updateAction) {
            throw new UnsupportedOperationException()
        }

        @Override
        CompilationState maybeUpdate(ObjectHolder.UpdateAction<CompilationState> updateAction) {
            throw new UnsupportedOperationException()
        }
    }

    class DummyResolver implements SourceIncludesResolver {
        final Map<String, TestFile> mapping = [:]
        final Set<TestFile> unresolved = []

        void unresolved(TestFile file) {
            unresolved.add(file)
        }

        void resolveAs(String include, TestFile file) {
            mapping[include] = file
        }

        @Override
        IncludeResolutionResult resolveInclude(File sourceFile, Include include, MacroLookup visibleMacros) {
            def deps = graph[sourceFile]
            assert deps != null
            def file = deps.find { it.name == include.value }
            assert file
            return new IncludeResolutionResult() {
                @Override
                boolean isComplete() {
                    return !unresolved.contains(sourceFile)
                }

                @Override
                Collection<SourceIncludesResolver.IncludeFile> getFiles() {
                    return [
                        new SourceIncludesResolver.IncludeFile() {
                            @Override
                            boolean isQuotedInclude() {
                                return false
                            }

                            @Override
                            String getPath() {
                                return include.value
                            }

                            @Override
                            File getFile() {
                                return file
                            }

                            @Override
                            HashCode getContentHash() {
                                return getContentHash(file)
                            }
                        }
                    ]
                }
            }
        }

        @Override
        IncludeFile resolveInclude(@Nullable File sourceFile, String includePath) {
            def file = mapping.get(includePath)
            if (file == null) {
                file = graph.keySet().find { it.name == includePath }
            }
            if (file == null) {
                return null
            }
            return new IncludeFile() {
                @Override
                boolean isQuotedInclude() {
                    return false
                }

                @Override
                String getPath() {
                    return includePath
                }

                @Override
                File getFile() {
                    return file
                }

                @Override
                HashCode getContentHash() {
                    return getContentHash(file)
                }
            }
        }
    }

    private HashCode getContentHash(File file) {
        fileSystemAccess.invalidate([file.absolutePath])
        return fileSystemAccess.readRegularFileContentHash(file.getAbsolutePath())
            .orElse(new MissingFileSnapshot(file.getAbsolutePath(), file.getName(), AccessType.DIRECT).hash)
    }
}
