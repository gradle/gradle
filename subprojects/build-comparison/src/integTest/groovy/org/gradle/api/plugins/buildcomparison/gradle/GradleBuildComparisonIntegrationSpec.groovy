/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.gradle

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.util.TestFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Rule

class GradleBuildComparisonIntegrationSpec extends WellBehavedPluginTest {
    private static final String NOT_IDENTICAL_MESSAGE_PREFIX = "The build outcomes were not found to be identical. See the report at: file:///"
    @Rule TestResources testResources

    @Override
    String getPluginId() {
        "compare-gradle-builds"
    }

    @Override
    String getMainTask() {
        "help"
    }

    def setup() {
        executer.withForkingExecuter()
        applyPlugin()
    }

    def compareArchives() {
        given:
        buildFile << """
            compareGradleBuilds {
                sourceBuild.projectDir "sourceBuild"
                targetBuild { projectDir "targetBuild" }
            }
        """

        when:
        fails "compareGradleBuilds"

        then:
        failedBecauseNotIdentical()

        def html = html()

        // Name of outcome
        html.select("h3").text() == "Task: “:jar”"

        // Entry comparisons
        def rows = html.select("table")[2].select("tr").tail().collectEntries { [it.select("td")[0].text(), it.select("td")[1].text()] }
        rows.size() == 4
        rows["org/gradle/ChangedClass.class"] == "entry in the Source Build is 409 bytes - in the Target Build it is 486 bytes (+77)"
        rows["org/gradle/DifferentCrcClass.class"] == "entries are of identical size but have different content"
        rows["org/gradle/SourceBuildOnlyClass.class"] == "Only exists in Source Build"
        rows["org/gradle/TargetBuildOnlyClass.class"] == "Only exists in Target Build"

        and:
        storedFile("source").exists()
        storedFile("source/_jar").list().toList() == ["testBuild.jar"]
        storedFile("target/_jar").list().toList() == ["testBuild.jar"]

        and: // old filestore not around
        !testDir.list().any { it.startsWith(CompareGradleBuilds.TMP_FILESTORAGE_PREFIX) }
    }

    void failedBecauseNotIdentical() {
        failure.assertHasCause(NOT_IDENTICAL_MESSAGE_PREFIX)
    }

    def "compare same project"() {
        given:
        buildFile << """
            apply plugin: "java"
        """

        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        run "compareGradleBuilds"

        then:
        html().select("p").text() == "The archives are completely identical."
        output.contains("The source build and target build are identical")
    }

    def "compare project with unknown outcomes"() {
        given:
        file("file.txt") << "text"
        buildFile << """
            apply plugin: "java-base"

            configurations {
                archives
            }

            task tarArchive(type: Tar) {
                from "file.txt"
            }

            artifacts {
                archives tarArchive
            }
        """

        when:
        fails "compareGradleBuilds"

        then:
        failedBecauseNotIdentical()

        html().select("p").text() == "This version of Gradle does not understand this kind of build outcome. Running the comparison process from a newer version of Gradle may yield better results."
    }

    def "cannot compare when both sides are old"() {
        when:
        buildFile << """
            compareGradleBuilds {
                sourceBuild.gradleVersion "1.1"
                targetBuild.gradleVersion "1.1"
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        failure.assertHasCause("Cannot run comparison because both the source and target build are to be executed with a Gradle version older than Gradle 1.2 (source: 1.1, target: 1.1).")
    }

    def "cannot compare when source is older than 1.0"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                sourceBuild.gradleVersion "1.0-rc-1"
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        failure.assertHasCause("Builds must be executed with Gradle 1.0 or newer (source: 1.0-rc-1, target: ${distribution.version})")
    }

    def "can ignore errors"() {
        given:
        file("file.txt") << "text"
        buildFile << """
            apply plugin: "java-base"

            configurations {
                archives
            }

            task tarArchive(type: Tar) {
                from "file.txt"
            }

            artifacts {
                archives tarArchive
            }

            compareGradleBuilds.ignoreFailures true
        """

        when:
        succeeds "compareGradleBuilds"

        then:
        output.contains(NOT_IDENTICAL_MESSAGE_PREFIX)
    }

    def "cannot compare when target is older than 1.0"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                targetBuild.gradleVersion "1.0-rc-1"
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        failure.assertHasCause("Builds must be executed with Gradle 1.0 or newer (source: ${distribution.version}, target: 1.0-rc-1)")
    }

    Document html(path = "build/reports/compareGradleBuilds/index.html") {
        Jsoup.parse(file(path), "utf8")
    }

    TestFile storedFile(String path, String base = "build/reports/compareGradleBuilds/files") {
        file("$base/$path")
    }
}
