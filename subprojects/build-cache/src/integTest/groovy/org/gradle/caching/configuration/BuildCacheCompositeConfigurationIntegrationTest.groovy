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

package org.gradle.caching.configuration

import org.gradle.caching.internal.FinalizeBuildCacheConfigurationBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.operations.trace.BuildOperationTrace

/**
 * Tests build cache configuration within composite builds and buildSrc.
 */
class BuildCacheCompositeConfigurationIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        executer.withBuildCacheEnabled()
    }

    def "can configure with settings.gradle"() {
        def buildSrcCacheDir = temporaryFolder.file("buildSrc-cache").createDir().absoluteFile
        def i1CacheDir = temporaryFolder.file("i1-cache").createDir().absoluteFile
        def i1BuildSrcCacheDir = temporaryFolder.file("i1-buildSrc-cache").createDir().absoluteFile
        def i2CacheDir = temporaryFolder.file("i2-cache").createDir().absoluteFile
        def i3CacheDir = temporaryFolder.file("i3-cache").createDir().absoluteFile

        settingsFile << """
            ${useLocalCache(cacheDir)}
            
            includeBuild "i1"
            includeBuild "i2"
        """

        file("buildSrc/settings.gradle") << useLocalCache(buildSrcCacheDir)
        file("i1/settings.gradle") << useLocalCache(i1CacheDir)
        file("i1/buildSrc/settings.gradle") << useLocalCache(i1BuildSrcCacheDir)
        file("i2/settings.gradle") << useLocalCache(i2CacheDir)

        buildFile << customTaskCode("root")
        file("buildSrc/build.gradle") << customTaskCode("buildSrc") << """
            build.dependsOn customTask
        """
        file("i1/build.gradle") << customTaskCode("i1")
        file("i1/buildSrc/build.gradle") << customTaskCode("i1:buildSrc") << """
            build.dependsOn customTask
        """
        file("i2/build.gradle") << customTaskCode("i2") << """

            task gradleBuild(type: GradleBuild) {
                dir = "../i3"
                tasks = ["customTask"]

                // Trace fixture doesn't work well with GradleBuild, turn it off 
                startParameter.systemPropertiesArgs["$BuildOperationTrace.SYSPROP"] = "false"
            }
            
            customTask.dependsOn gradleBuild
        """
        file("i3/settings.gradle") << useLocalCache(i3CacheDir)
        file("i3/build.gradle") << customTaskCode("i3")

        buildFile << """
            task all { dependsOn gradle.includedBuilds*.task(':customTask'), tasks.customTask } 
        """

        expect:
        succeeds "all", "-i"

        and:
        listCacheFiles().size() == 4 // root, i1, i1BuildSrc, i2
        i1CacheDir.listFiles().size() == 0
        i1BuildSrcCacheDir.listFiles().size() == 0
        i2CacheDir.listFiles().size() == 0

        listCacheFiles(buildSrcCacheDir).size() == 1
        listCacheFiles(i3CacheDir).size() == 1

        and:
        result.assertOutputContains "Using local directory build cache for build ':buildSrc' (location = ${buildSrcCacheDir}, targetSize = 5 GB)."
        result.assertOutputContains "Using local directory build cache for build ':i2:i3' (location = ${i3CacheDir}, targetSize = 5 GB)."
        result.assertOutputContains "Using local directory build cache for the root build (location = ${cacheDir}, targetSize = 5 GB)."

        and:
        def finalizeOps = operations.all(FinalizeBuildCacheConfigurationBuildOperationType)
        finalizeOps.size() == 2
        def pathToCacheDirMap = finalizeOps.collectEntries {
            [
                it.details.buildPath,
                new File(it.result.local.config.location as String)
            ]
        } as Map<String, File>

        pathToCacheDirMap == [
            ":": cacheDir,
            ":buildSrc": buildSrcCacheDir
        ]
    }

    private static String customTaskCode(String val = "foo") {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @Input
                String val

                @OutputFile
                File outputFile = new File(temporaryDir, "output.txt")

                @TaskAction
                void generate() {
                    outputFile.text = val
                }
            }

            task customTask(type: CustomTask) { val = "$val" }
        """
    }

    private static String useLocalCache(File dir) {
        """
            buildCache {
                local(DirectoryBuildCache) {
                    directory = '${dir.toURI().toString()}'
                }
            }
        """
    }
}
