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

package org.gradle.caching.configuration.internal

import org.gradle.caching.internal.FinalizeBuildCacheConfigurationBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue

import static org.gradle.integtests.fixtures.executer.GradleContextualExecuter.isConfigCache
import static org.gradle.integtests.fixtures.executer.GradleContextualExecuter.isNotConfigCache

/**
 * Tests build cache configuration within composite builds and buildSrc.
 */
class BuildCacheCompositeConfigurationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    enum EnabledBy {
        INVOCATION_SWITCH,
        PROGRAMMATIC
    }

    @ToBeFixedForConfigurationCache(
        because = "startParameter.buildCacheEnabled is not restored",
        iterationMatchers = ['^.+PROGRAMMATIC$']
    )
    def "can configure with settings.gradle - enabled by #by"() {
        def enablingCode = by == EnabledBy.PROGRAMMATIC ? """\ngradle.startParameter.buildCacheEnabled = true\n""" : ""
        if (by == EnabledBy.INVOCATION_SWITCH) {
            executer.beforeExecute {
                withBuildCacheEnabled()
            }
        }

        def mainCache = new TestBuildCache(file("main-cache"))
        def buildSrcCache = new TestBuildCache(file("buildSrc-cache"))
        def i1Cache = new TestBuildCache(file("i1-cache"))
        def i1BuildSrcCache = new TestBuildCache(file("i1-buildSrc-cache"))
        def i2Cache = new TestBuildCache(file("i2-cache"))
        def i3Cache = new TestBuildCache(file("i3-cache"))

        settingsFile << mainCache.localCacheConfiguration() << enablingCode << """
            includeBuild "i1"
            includeBuild "i2"
        """

        file("buildSrc/settings.gradle") << buildSrcCache.localCacheConfiguration() << enablingCode
        file("i1/settings.gradle") << i1Cache.localCacheConfiguration() << enablingCode
        file("i1/buildSrc/settings.gradle") << i1BuildSrcCache.localCacheConfiguration() << enablingCode
        file("i2/settings.gradle") << i2Cache.localCacheConfiguration() << enablingCode

        buildFile << customTaskCode("root")
        file("buildSrc/build.gradle") << customTaskCode("buildSrc") << """
            jar.dependsOn customTask
        """
        file("i1/build.gradle") << customTaskCode("i1")
        file("i1/buildSrc/build.gradle") << customTaskCode("i1:buildSrc") << """
            jar.dependsOn customTask
        """
        file("i2/build.gradle") << customTaskCode("i2")
        if (isNotConfigCache()) { // GradleBuild is not supported with the configuration cache
            file("i2/build.gradle") << """

                task gradleBuild(type: GradleBuild) {
                    dir = "../i3"
                    tasks = ["customTask"]
                }

                customTask.dependsOn gradleBuild
            """
            file("i3/settings.gradle") << i3Cache.localCacheConfiguration() << enablingCode
            file("i3/build.gradle") << customTaskCode("i3")
        }

        buildFile << """
            task all { dependsOn gradle.includedBuilds*.task(':customTask'), tasks.customTask }
        """

        expect:
        succeeds "all", "-i"

        and:
        i1Cache.empty
        i1BuildSrcCache.empty
        i2Cache.empty
        buildSrcCache.empty
        mainCache.listCacheFiles().size() == 5 // root, i1, i1BuildSrc, i2, buildSrc
        isConfigCache() || i3Cache.listCacheFiles().size() == 1

        and:
        if (isNotConfigCache()) {
            outputContains "Using local directory build cache for build ':i2:i3' (location = ${i3Cache.cacheDir}, removeUnusedEntriesAfter = 7 days)."
        }
        outputContains "Using local directory build cache for the root build (location = ${mainCache.cacheDir}, removeUnusedEntriesAfter = 7 days)."

        and:
        def expectedCacheDirs = [":": mainCache.cacheDir]
        if (isNotConfigCache()) {
            expectedCacheDirs[":i2:i3"] = i3Cache.cacheDir
        }

        def finalizeOps = operations.all(FinalizeBuildCacheConfigurationBuildOperationType)
        finalizeOps.size() == expectedCacheDirs.size()
        def pathToCacheDirMap = finalizeOps.collectEntries {
            [
                it.details.buildPath,
                new File(it.result.local.config.location as String)
            ]
        } as Map<String, File>

        pathToCacheDirMap == expectedCacheDirs

        when:
        file("i1/build").forceDeleteDir()
        file("i2/build").forceDeleteDir()

        and:
        succeeds "all", "-i"

        then:
        result.assertTaskSkipped(':all')
        result.groupedOutput.task(':i1:customTask').outcome == 'FROM-CACHE'
        result.groupedOutput.task(':i2:customTask').outcome == 'FROM-CACHE'

        where:
        by << EnabledBy.values()
    }

    @Issue("https://github.com/gradle/gradle/issues/4216")
    def "build cache service is closed only after all included builds are finished"() {
        executer.beforeExecute { it.withBuildCacheEnabled() }
        def localCache = new TestBuildCache(file("local-cache"))
        settingsFile << localCache.localCacheConfiguration(true)

        buildTestFixture.withBuildInSubDir()
        multiProjectBuild('included', ['first', 'second']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java-library'

                    tasks.withType(Jar) {
                        doFirst {
                            // this makes it more probable that tasks from the included build finish after the root build
                            Thread.sleep(1000)
                        }
                    }
                }
                tasks.build.dependsOn(subprojects.tasks.build)
                tasks.clean.dependsOn(subprojects.tasks.clean)
            """
            file("src/test/java/Test.java") << """class Test {}"""
            file("first/src/test/java/Test.java") << """class TestFirst {}"""
            file("second/src/test/java/Test.java") << """class TestSecond {}"""
        }

        settingsFile << localCache.localCacheConfiguration() << """
            includeBuild "included"
        """
        buildFile << """
            apply plugin: 'java-library'
        """

        expect:
        succeeds "build", ":included:build"
        succeeds "clean", ":included:clean"
        succeeds "build", ":included:build", "--info"

        and:
        // Will run after the root build has finished
        output.contains("> Task :included:second:test FROM-CACHE")
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
