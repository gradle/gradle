/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.play.integtest.fixtures.Repositories.PLAY_REPOSITORES

class PlayDistributionPluginIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        settingsFile << """ rootProject.name = 'dist-play-app' """
        buildFile << """
            plugins {
                id 'play'
            }

            ${PLAY_REPOSITORES}
        """
    }

    @Unroll
    def "uses unique names for dependency jar in distribution when #description"() {
        given:
        (1..2).each { subprojectIdx ->
            def subprojectName = "sub${subprojectIdx}"
            settingsFile << """
                include '${subprojectName}:dependency'
            """
            def dependencyRoot = file("${subprojectName}/dependency")
            def srcDir = dependencyRoot.file("src/main/java/${subprojectName}")
            srcDir.mkdirs()
            srcDir.file("Dependency.java") << """
                package ${subprojectName};
                public class Dependency {}"""
            dependencyRoot.file("build.gradle") << """
                apply plugin: 'java'
                group = 'com.example.${subprojectName}'
                version = "1.${subprojectIdx}"
"""
            buildFile << """
                dependencies {
                    playRun project(":${subprojectName}:dependency")
                }
"""
        }
        when:
        succeeds "dist"

        then:
        executedAndNotSkipped(":createPlayBinaryDist")

        zip("build/distributions/playBinary.zip").containsDescendants(
            "playBinary/lib/sub1.dependency-dependency-1.1.jar",
            "playBinary/lib/sub2.dependency-dependency-1.2.jar")

        when:
        file("sub1/dependency/build.gradle") << "version = '2.0'"
        and:
        succeeds "dist"
        then:
        executedAndNotSkipped(":createPlayBinaryDist")

        zip("build/distributions/playBinary.zip").doesNotContainDescendants("playBinary/lib/sub1.dependency-dependency-1.1.jar").containsDescendants(
            "playBinary/lib/sub1.dependency-dependency-2.0.jar",
            "playBinary/lib/sub2.dependency-dependency-1.2.jar")
    }

    def "builds empty distribution when no sources present" () {
        buildFile << """
            model {
                tasks.createPlayBinaryStartScripts {
                    doLast {
                        assert classpath.contains(file(createPlayBinaryDistributionJar.archivePath))
                    }
                }
                tasks.createPlayBinaryDist {
                    doLast {
                        def zipFileNames = zipTree(archivePath).collect { it.name }
                        configurations.playRun.collect { it.name }.each { assert zipFileNames.contains(it) }
                    }
                }
            }
        """

        when:
        succeeds "stage"

        then:
        executedAndNotSkipped(
                ":createPlayBinaryJar",
                ":createPlayBinaryDistributionJar",
                ":createPlayBinaryAssetsJar",
                ":createPlayBinaryStartScripts",
                ":stagePlayBinaryDist")
        skipped(":compilePlayBinaryScala")
        notExecuted(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayTwirlTemplates")

        and:
        file("build/stage/playBinary").assertContainsDescendants(
                "lib/dist-play-app.jar",
                "lib/dist-play-app-assets.jar",
                "bin/playBinary",
                "bin/playBinary.bat"
        )
        if (OperatingSystem.current().linux || OperatingSystem.current().macOsX) {
            assert file("build/stage/playBinary/bin/playBinary").mode == 0755
        }

        when:
        succeeds "dist"

        then:
        executedAndNotSkipped(":createPlayBinaryDist")
        skipped(
                ":compilePlayBinaryScala",
                ":createPlayBinaryJar",
                ":createPlayBinaryDistributionJar",
                ":createPlayBinaryAssetsJar",
                ":createPlayBinaryStartScripts")
        notExecuted(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayTwirlTemplates")

        and:
        zip("build/distributions/playBinary.zip").containsDescendants(
                "playBinary/lib/dist-play-app.jar",
                "playBinary/lib/dist-play-app-assets.jar",
                "playBinary/bin/playBinary",
                "playBinary/bin/playBinary.bat"
        )
    }

    ZipTestFixture zip(String path) {
        return new ZipTestFixture(file(path))
    }
}
