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

import org.gradle.api.plugins.buildcomparison.fixtures.BuildComparisonHtmlReportFixture
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.file.TestFile
import org.jsoup.Jsoup
import org.junit.Rule

class BuildComparisonIntegrationSpec extends WellBehavedPluginTest {
    private static final String NOT_IDENTICAL_MESSAGE_PREFIX = "The build outcomes were not found to be identical. See the report at: file:///"
    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    @Override
    String getPluginName() {
        "compare-gradle-builds"
    }

    @Override
    String getMainTask() {
        "help"
    }

    def setup() {
        executer.expectDeprecationWarning()
        executer.requireGradleDistribution()
        applyPlugin()
    }

    def "emits deprecation warning"() {
        given:
        applyPlugin()

        when:
        succeeds("help")

        then:
        outputContains("The Build Comparison plugin has been deprecated")
    }

    def compareSimpleArchives() {
        when:
        buildsCompared("source", "target")

        then:
        failedBecauseNotIdentical()

        def report = report()
        report.outcomeName == ":jar"

        def rows = report.entries
        rows.size() == 7
        rows["org/gradle/Changed.class"] == "entry in the source build is 394 bytes - in the target build it is 471 bytes (+77)"
        rows["org/gradle/DifferentCrc.class"] == "entries are of identical size but have different content"
        rows["org/gradle/SourceBuildOnly.class"] == "entry does not exist in target build archive"
        rows["org/gradle/TargetBuildOnly.class"] == "entry does not exist in source build archive"
        rows["someSource.properties"] == "entry does not exist in target build archive"
        rows["someTarget.properties"] == "entry does not exist in source build archive"
        rows["dir1/different.txt"] == "entries are of identical size but have different content"

        and:
        storedFile("source").exists()
        storedFile("source/_jar").list().toList() == ["testBuild.jar"]
        storedFile("target/_jar").list().toList() == ["testBuild.jar"]

        and: // old filestore not around
        !testDirectory.list().any { it.startsWith(CompareGradleBuilds.TMP_FILESTORAGE_PREFIX) }
    }

    def compareNestedArchives() {
        when:
        buildsCompared("source", "target")

        then:
        failedBecauseNotIdentical()

        def report = report()
        report.outcomeName == ":jar"

        // Entry comparisons
        def rows = report.entries

        rows.size() == 4
        rows["sourceSub.zip"] == "entry does not exist in target build archive"
        rows["targetSub.zip"] == "entry does not exist in source build archive"
        rows["dir2/differentSub.zip"] == "entry in the source build is 688 bytes - in the target build it is 689 bytes (+1)"
        rows["dir2/differentSub.zip!/a.txt"] == "entry in the source build is 0 bytes - in the target build it is 1 bytes (+1)"

        and:
        storedFile("source").exists()
        storedFile("source/_jar").list().toList() == ["testBuild.jar"]
        storedFile("target/_jar").list().toList() == ["testBuild.jar"]

        and: // old filestore not around
        !testDirectory.list().any { it.startsWith(CompareGradleBuilds.TMP_FILESTORAGE_PREFIX) }
    }


    def buildsCompared(String source, String target) {
        buildFile << """
            compareGradleBuilds {
                sourceBuild.projectDir "$source"
                targetBuild { projectDir "$target" }
            }
        """
        fails "compareGradleBuilds"
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
        report().identical
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

        report().select("h3")[0].nextSibling().nextSibling().text() == "This version of Gradle does not understand this kind of build outcome."
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
        failure.assertHasCause("Builds must be executed with Gradle 1.2 or newer (source: 1.1, target: 1.1)")
    }

    def "cannot compare when source is older than 1.2"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                sourceBuild.gradleVersion "1.1"
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        failure.assertHasCause("Builds must be executed with Gradle 1.2 or newer (source: 1.1, target: ${distribution.version.version})")
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

    def "cannot compare when target is older than 1.2"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                targetBuild.gradleVersion "1.1"
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        failure.assertHasCause("Builds must be executed with Gradle 1.2 or newer (source: ${distribution.version.version}, target: 1.1)")
    }

    def "can handle artifact not existing on source side"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                sourceBuild.arguments = ["-PnoJar"]
            }

            if (project.hasProperty("noJar")) {
                jar.enabled = false
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        report().getResult(":jar") == "The archive was only produced by the target build."
    }

    def "can handle artifact not existing on target side"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                targetBuild.arguments = ["-PnoJar"]
            }

            if (project.hasProperty("noJar")) {
                jar.enabled = false
            }
        """

        then:
        fails "compareGradleBuilds"

        and:
        report().getResult(":jar") == "The archive was only produced by the source build."
    }

    def "can handle uncompared outcomes"() {
        when:
        buildFile << """
            apply plugin: "java"
            compareGradleBuilds {
                sourceBuild.arguments = ["-PdoJavadoc"]
                targetBuild.arguments = ["-PdoSource"]

            }

            if (project.hasProperty("doSource")) {
                task sourceJar(type: Jar) {
                    from sourceSets.main.allJava
                    classifier "source"
                }
                artifacts {
                    archives sourceJar
                }
            }

            if (project.hasProperty("doJavadoc")) {
                task javadocJar(type: Jar) {
                    from tasks.javadoc
                    classifier "javadoc"
                }
                artifacts {
                    archives javadocJar
                }
            }
        """

        and:
        file("src/main/java/Thing.java") << "public class Thing {}"

        then:
        fails "compareGradleBuilds"

        def report = report()
        report.select("h2").find { it.text() == "Uncompared source outcomes" }
        report.select("h2").find { it.text() == "Uncompared target outcomes" }

        report.select(".build-outcome.source h3").text() == ":javadocJar"
        report.select(".build-outcome.target h3").text() == ":sourceJar"
    }

    BuildComparisonHtmlReportFixture report(path = "build/reports/compareGradleBuilds/index.html") {
        new BuildComparisonHtmlReportFixture(Jsoup.parse(file(path), null))
    }

    TestFile storedFile(String path, String base = "build/reports/compareGradleBuilds/files") {
        file("$base/$path")
    }
}

