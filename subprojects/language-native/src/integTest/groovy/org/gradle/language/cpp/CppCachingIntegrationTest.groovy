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

package org.gradle.language.cpp

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibraries
import org.gradle.test.fixtures.file.TestFile

class CppCachingIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements DirectoryBuildCacheFixture, CppTaskNames {
    CppAppWithLibraries app = new CppAppWithLibraries()

    def setupProject(TestFile project = temporaryFolder.testDirectory) {
        project.file('settings.gradle') << "include 'lib1', 'lib2'"
        project.file('settings.gradle') << localCacheConfiguration()
        project.file('build.gradle').text = """
            apply plugin: 'cpp-executable'
            dependencies {
                implementation project(':lib1')
            }

            project(':lib1') {
                apply plugin: 'cpp-library'
                dependencies {
                    implementation project(':lib2')
                }
            }
            project(':lib2') {
                apply plugin: 'cpp-library'
            }
        """
        app.greeterLib.writeToProject(project.file('lib1'))
        app.loggerLib.writeToProject(project.file("lib2"))
        app.main.writeToProject(project)
        executer.beforeExecute {
            withArgument("-Dorg.gradle.caching.native=true")
        }
    }

    def 'compilation can be cached'() {
        def f = new NativeCachingFixture(buildType: buildType)
        setupProject()

        when:
        withBuildCache().run f.compilationTask

        then:
        f.compileIsNotCached()

        when:
        withBuildCache().run 'clean', installTask(buildType)

        then:
        f.compileIsCached()
        installation("build/install/main/${buildType.toLowerCase()}").exec().out == app.expectedOutput

        where:
        buildType << [debug, release]
    }

    def "compilation task is relocatable"() {
        def f = new NativeCachingFixture(buildType: buildType)
        def originalLocation = file('original-location')
        def newLocation = file('new-location')
        setupProject(originalLocation)
        setupProject(newLocation)

        when:
        inDirectory(originalLocation)
        withBuildCache().run f.compilationTask

        def snapshotsInOriginalLocation = f.snapshotObjects(originalLocation)

        then:
        executedAndNotSkipped f.compilationTask

        when:
        executer.beforeExecute {
            usingProjectDirectory(newLocation)
        }
        run f.compilationTask

        then:
        executedAndNotSkipped(f.compilationTask)
        f.assertSameSnapshots(snapshotsInOriginalLocation, f.snapshotObjects(newLocation))

        when:
        run 'clean'
        withBuildCache().run f.compilationTask, installTask(buildType)

        then:
        skipped f.compilationTask
        f.assertSameSnapshots(snapshotsInOriginalLocation, f.snapshotObjects(newLocation))
        installation(newLocation.file("build/install/main/${buildType.toLowerCase()}")).exec().out == app.expectedOutput

        where:
        buildType << [debug, release]
    }

    String getSourceType() {
        return 'Cpp'
    }

    class NativeCachingFixture {
        String buildType

        void assertSameSnapshots(Map<String, TestFile.Snapshot> snapshotsInOriginalLocation, Map<String, TestFile.Snapshot> snapshotsInNewLocation) {
            assert snapshotsInOriginalLocation.keySet() == snapshotsInNewLocation.keySet()
            if (nonDeterministicCompilation || absolutePathsInFile) {
                return
            }
            snapshotsInOriginalLocation.each { path, originalSnapshot ->
                def newSnapshot = snapshotsInNewLocation[path]
                assert originalSnapshot.hash == newSnapshot.hash
            }
        }

        boolean isAbsolutePathsInFile() {
            buildType == debug || clangOnLinux
        }

        static boolean isClangOnLinux() {
            toolChain.displayName == "clang" && OperatingSystem.current().isLinux()
        }

        Map<String, TestFile.Snapshot> snapshotObjects(TestFile projectDir) {
            def objDir = projectDir.file('build/obj')
            def objects = objDir.allDescendants()
            def snapshots = objects.collectEntries { path ->
                def obj = objDir.file(path)
                if (!obj.isFile()) {
                    return null
                }
                [(path): obj.snapshot()]
            }
            return snapshots
        }

        String getCompilationTask() {
            ":compile${buildType}Cpp"
        }

        void compileIsCached(TestFile projectDir = temporaryFolder.testDirectory) {
            skipped compilationTask
            objectFileFor(projectDir.file('src/main/cpp/main.cpp'), "build/obj/main/${buildType.toLowerCase()}").assertExists()
        }

        void compileIsNotCached() {
            executedAndNotSkipped compilationTask
        }
    }

}
