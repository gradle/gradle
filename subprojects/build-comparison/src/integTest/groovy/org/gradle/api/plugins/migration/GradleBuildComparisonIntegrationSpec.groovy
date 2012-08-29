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

package org.gradle.api.plugins.migration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Rule

class GradleBuildComparisonIntegrationSpec extends AbstractIntegrationSpec {
    @Rule TestResources testResources

    def setup() {
        executer.withForkingExecuter()
    }

    def compareArchives() {
        given:
        buildFile << """
            task compare(type: CompareGradleBuilds) {
                reportDir "result"
                sourceBuild.projectDir "sourceBuild"
                targetBuild { projectDir "targetBuild" }
            }
        """

        when:
        run("compare")

        then:
        def html = html("result/index.html")

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
        file("result/files/source").exists()
        file("result/files/source/_jar").list().toList() == ["testBuild.jar"]
        file("result/files/target/_jar").list().toList() == ["testBuild.jar"]
    }

    def "compare same project"() {
        given:
        settingsFile << "" // stop up search
        buildFile << """
            apply plugin: "java"

            task compare(type: CompareGradleBuilds) {
                reportDir "result"
            }
        """

        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        run "compare"

        then:
        html("result/index.html").select("p").text() == "The archives are completely identical."
    }

    def "compare project with unknown outcomes"() {
        given:
        settingsFile << "" // stop up search
        file("file.txt") << "text"
        buildFile << """
            apply plugin: "java-base"

            configurations {
                archives
            }

            task compare(type: CompareGradleBuilds) {
                reportDir "result"
            }

            task tarArchive(type: Tar) {
                from "file.txt"
            }

            artifacts {
                archives tarArchive
            }
        """

        when:
        run "compare"

        then:
        html("result/index.html").select("p").text() == "This version of Gradle does not understand this kind of build outcome. Running the comparison process from a newer version of Gradle may yield better results."
    }

    Document html(path) {
        Jsoup.parse(file(path), "utf8")
    }
}
