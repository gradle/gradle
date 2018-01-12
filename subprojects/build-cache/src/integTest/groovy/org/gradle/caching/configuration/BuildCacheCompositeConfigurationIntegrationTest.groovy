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
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.internal.operations.trace.BuildOperationTrace
/**
 * Tests build cache configuration within composite builds and buildSrc.
 */
class BuildCacheCompositeConfigurationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        executer.withBuildCacheEnabled()
    }

    def "can configure with settings.gradle"() {
        def mainCache = new TestBuildCache(file("main-cache"))
        def buildSrcCache = new TestBuildCache(file("buildSrc-cache"))
        def i1Cache = new TestBuildCache(file("i1-cache"))
        def i1BuildSrcCache = new TestBuildCache(file("i1-buildSrc-cache"))
        def i2Cache = new TestBuildCache(file("i2-cache"))
        def i3Cache = new TestBuildCache(file("i3-cache"))

        settingsFile << mainCache.localCacheConfiguration() << """
            includeBuild "i1"
            includeBuild "i2"
        """

        file("buildSrc/settings.gradle") << buildSrcCache.localCacheConfiguration()
        file("i1/settings.gradle") << i1Cache.localCacheConfiguration()
        file("i1/buildSrc/settings.gradle") << i1BuildSrcCache.localCacheConfiguration()
        file("i2/settings.gradle") << i2Cache.localCacheConfiguration()

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
        file("i3/settings.gradle") << i3Cache.localCacheConfiguration()
        file("i3/build.gradle") << customTaskCode("i3")

        buildFile << """
            task all { dependsOn gradle.includedBuilds*.task(':customTask'), tasks.customTask } 
        """

        expect:
        succeeds "all", "-i"

        and:
        i1Cache.assertEmpty()
        i1BuildSrcCache.assertEmpty()
        i2Cache.assertEmpty()
        mainCache.listCacheFiles().size() == 4 // root, i1, i1BuildSrc, i2

        buildSrcCache.listCacheFiles().size() == 1
        i3Cache.listCacheFiles().size() == 1

        and:
        result.assertOutputContains "Using local directory build cache for build ':buildSrc' (location = ${buildSrcCache.cacheDir}, removeUnusedEntriesAfter = 7 days)."
        result.assertOutputContains "Using local directory build cache for build ':i2:i3' (location = ${i3Cache.cacheDir}, removeUnusedEntriesAfter = 7 days)."
        result.assertOutputContains "Using local directory build cache for the root build (location = ${mainCache.cacheDir}, removeUnusedEntriesAfter = 7 days)."

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
            ":": mainCache.cacheDir,
            ":buildSrc": buildSrcCache.cacheDir
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
}
