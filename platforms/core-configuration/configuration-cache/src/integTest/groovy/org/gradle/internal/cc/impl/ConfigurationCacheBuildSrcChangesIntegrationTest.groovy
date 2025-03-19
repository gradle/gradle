/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.cc.impl.fixtures.BuildLogicChangeFixture

import static org.junit.Assume.assumeFalse

class ConfigurationCacheBuildSrcChangesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = newConfigurationCacheFixture()

    def "invalidates cache upon change to buildSrc #changeFixtureSpec"() {
        given:
        def changeFixture = changeFixtureSpec.fixtureForProjectDir(file('buildSrc'))
        changeFixture.setup()
        buildFile << """
            plugins { id('$changeFixture.pluginId') }
        """

        when:
        configurationCacheRun changeFixture.task

        then:
        outputContains changeFixture.expectedOutputBeforeChange
        configurationCache.assertStateStored()

        when:
        changeFixture.applyChange()
        configurationCacheRun changeFixture.task

        then:
        outputContains changeFixture.expectedCacheInvalidationMessage
        outputContains changeFixture.expectedOutputAfterChange
        configurationCache.assertStateStored()

        when:
        configurationCacheRun changeFixture.task

        then:
        outputContains changeFixture.expectedOutputAfterChange
        configurationCache.assertStateLoaded()

        where:
        changeFixtureSpec << BuildLogicChangeFixture.specs()
    }

    def "invalidates cache upon change to #inputName used by buildSrc"() {

        assumeFalse(
            'property from gradle.properties is not available to buildSrc',
            inputName == 'gradle.properties'
        )

        given:
        file("buildSrc/build.gradle.kts").text = """

            interface Params: $ValueSourceParameters.name {
                val value: Property<String>
            }

            abstract class IsCi : $ValueSource.name<String, Params> {
                override fun obtain(): String? = parameters.value.orNull
            }

            val ciProvider = providers.of(IsCi::class.java) {
                parameters.value.set(providers.systemProperty("test_is_ci"))
            }

            val isCi = ${inputExpression}
            tasks {
                if (isCi.isPresent) {
                    register("run") {
                        doLast { println("ON CI") }
                    }
                } else {
                    register("run") {
                        doLast { println("NOT CI") }
                    }
                }
                jar {
                    dependsOn("run")
                }
            }
        """
        buildFile << """
            task assemble
        """

        when:
        configurationCacheRun "assemble"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "assemble"

        then: "buildSrc doesn't build"
        output.count("CI") == 0
        configurationCache.assertStateLoaded()

        when:
        if (inputName == 'gradle.properties') {
            file('gradle.properties').text = 'test_is_ci=true'
            configurationCacheRun "assemble"
        } else {
            configurationCacheRun "assemble", inputArgument
        }

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateStored()

        where:
        inputName             | inputExpression                          | inputArgument
        'custom value source' | 'ciProvider'                             | '-Dtest_is_ci=true'
        'system property'     | 'providers.systemProperty("test_is_ci")' | '-Dtest_is_ci=true'
        'Gradle property'     | 'providers.gradleProperty("test_is_ci")' | '-Ptest_is_ci=true'
        'gradle.properties'   | 'providers.gradleProperty("test_is_ci")' | ''
    }

    def "invalidates cache upon change to presence of valid buildSrc by creating #buildSrcNewFile"() {
        given:
        def buildOperations = newBuildOperationsFixture()
        settingsFile """
            rootProject.name = "root"
        """

        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        buildOperations.none("Load build (:buildSrc)")

        when:
        file("buildSrc/$buildSrcNewFile").touch()
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because a buildSrc build at 'buildSrc' has been added.")
        buildOperations.only("Load build (:buildSrc)")

        when:
        file("buildSrc").deleteDir()
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because ${withPlatformPath(removalMessage)}")
        buildOperations.none("Load build (:buildSrc)")

        where:
        _ | buildSrcNewFile                     | removalMessage
        _ | "settings.gradle"                   | "file 'buildSrc/settings.gradle' has been removed."
        _ | "build.gradle"                      | "an input to task ':buildSrc:jar' has changed"
        _ | "src/main/groovy/MyClass.groovy"    | "an input to task ':buildSrc:compileGroovy' has changed."
    }

    def "invalidates cache upon change to presence of valid buildSrc in #parentBuild build by creating #buildSrcNewFile"() {
        given:
        def buildOperations = newBuildOperationsFixture()
        settingsFile """
            rootProject.name = "root"
            includeBuild("included")
        """
        settingsFile "included/settings.gradle", ""
        settingsFile "$parentBuild/settings.gradle", """
            rootProject.name = "parent-of-buildSrc"
        """

        def buildSrcBuildPath = ":$parentBuild:buildSrc"
        def buildSrcDir = "$parentBuild/buildSrc"
        def ccReasonPath = withPlatformPath(buildSrcDir)

        when:
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        buildOperations.none("Load build ($buildSrcBuildPath)")

        when:
        file("$buildSrcDir/$buildSrcNewFile").touch()
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because a buildSrc build at '$ccReasonPath' has been added.")
        buildOperations.only("Load build ($buildSrcBuildPath)")

        when:
        file(buildSrcDir).deleteDir()
        configurationCacheRun "help"
        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because ${withPlatformPath(removalMessage)}")
        buildOperations.none("Load build ($buildSrcBuildPath)")

        where:
        parentBuild | buildSrcNewFile                   | removalMessage
        "included"  | "settings.gradle"                 | "file 'included/buildSrc/settings.gradle' has been removed."
        "included"  | "build.gradle"                    | "an input to task ':included:buildSrc:jar' has changed."
        "included"  | "src/main/groovy/MyClass.groovy"  | "an input to task ':included:buildSrc:compileGroovy' has changed."
        "buildSrc"  | "settings.gradle"                 | "file 'buildSrc/buildSrc/settings.gradle' has been removed."
        "buildSrc"  | "build.gradle"                    | "an input to task ':buildSrc:buildSrc:jar' has changed."
        "buildSrc"  | "src/main/groovy/MyClass.groovy"  | "an input to task ':buildSrc:buildSrc:compileGroovy' has changed."
    }

    def "reuses cache upon changing invalid buildSrc by creating #description"() {
        def changeFile = file(path)

        when:
        configurationCacheRun "help"

        then:
        configurationCache.assertStateStored()

        when:
        if (isDir) {
            changeFile.createDir()
        } else {
            changeFile.touch()
        }
        configurationCacheRun "help"
        then:
        configurationCache.assertStateLoaded()

        when:
        if (isDir) {
            changeFile.deleteDir()
        } else {
            changeFile.delete()
        }
        configurationCacheRun "help"

        then:
        configurationCache.assertStateLoaded()

        where:
        description          | isDir | path
        "buildSrc dir"       | true  | "buildSrc"
        "buildSrc file"      | false | "buildSrc"
        "buildSrc with file" | false | "buildSrc/not-build.gradle"
        "buildSrc with dir"  | false | "buildSrc/not-src/main/groovy/MyClass.groovy"
    }

    private static String withPlatformPath(CharSequence buildSrcDir) {
        return buildSrcDir.replace("/", File.separator)
    }
}
