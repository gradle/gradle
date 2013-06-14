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
package org.gradle.api.tasks.diagnostics

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import java.nio.charset.StandardCharsets

/**
 * Integration tests for the HTML dependency report generation task
 * @author JB Nizet
 */
class HtmlDependencyReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "renders graph"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "qix", "1.0").publish()
        mavenRepo.module("foo", "baz", "1.0").dependsOn("foo", "bar", "1.0").dependsOn("foo", "qix", "1.0").publish()

        file("settings.gradle") << """
            rootProject.name = 'fooProject'
        """

        file("build.gradle") << """
            apply plugin : 'project-report'
            description = 'dummy description'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { compile }
            configurations { testCompile }
            dependencies {
              compile 'foo:baz:1.0'
              testCompile 'foo:bar:1.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")

        then:
        json.generationDate != null
        json.gradleVersion != null
        json.project.name == "fooProject"
        json.project.description == "dummy description"
        json.project.configurations.size == 2
        json.project.configurations[0].name == "compile"
        json.project.configurations[0].dependencies.size == 1
        json.project.configurations[0].dependencies[0].module == "foo:baz"
        json.project.configurations[0].dependencies[0].name == "foo:baz:1.0"
        json.project.configurations[0].dependencies[0].resolvable == true
        json.project.configurations[0].dependencies[0].alreadyRendered == false
        json.project.configurations[0].dependencies[0].hasConflict == false
        json.project.configurations[0].dependencies[0].children.size == 2

        json.project.configurations[0].dependencies[0].children[0].module == "foo:bar"
        json.project.configurations[0].dependencies[0].children[0].name == "foo:bar:1.0"
        json.project.configurations[0].dependencies[0].children[0].resolvable == true
        json.project.configurations[0].dependencies[0].children[0].alreadyRendered == false
        json.project.configurations[0].dependencies[0].children[0].hasConflict == false
        json.project.configurations[0].dependencies[0].children[0].children.size == 0

        json.project.configurations[0].dependencies[0].children[1].module == "foo:qix"
        json.project.configurations[0].dependencies[0].children[1].name == "foo:qix:1.0"
        json.project.configurations[0].dependencies[0].children[1].resolvable == true
        json.project.configurations[0].dependencies[0].children[1].alreadyRendered == false
        json.project.configurations[0].dependencies[0].children[1].hasConflict == false
        json.project.configurations[0].dependencies[0].children[1].children.size == 0

        json.project.configurations[1].name == "testCompile"
        json.project.configurations[1].dependencies.size == 1
        json.project.configurations[1].dependencies[0].module == "foo:bar"
        json.project.configurations[1].dependencies[0].name == "foo:bar:1.0"
        json.project.configurations[1].dependencies[0].resolvable == true
        json.project.configurations[1].dependencies[0].alreadyRendered == false
        json.project.configurations[1].dependencies[0].hasConflict == false
        json.project.configurations[1].dependencies[0].children.size == 0
    }

    def "already rendered dependencies are marked as such"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "qix", "1.0").dependsOn("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "baz", "1.0").dependsOn("foo", "qix", "1.0").publish()

        file("settings.gradle") << """
            rootProject.name = 'fooProject'
        """

        file("build.gradle") << """
            apply plugin : 'project-report'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { compile }
            dependencies {
              compile 'foo:qix:1.0'
              compile 'foo:baz:1.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")

        then:
        json.project.configurations[0].dependencies.size == 2
        json.project.configurations[0].dependencies[0].name == "foo:qix:1.0"
        json.project.configurations[0].dependencies[0].children[0].name == "foo:bar:1.0"

        json.project.configurations[0].dependencies[1].name == "foo:baz:1.0"
        json.project.configurations[0].dependencies[1].children[0].name == "foo:qix:1.0"
        json.project.configurations[0].dependencies[1].children[0].alreadyRendered == true
        json.project.configurations[0].dependencies[1].children[0].children.empty
    }

    def "non-resolved dependencies are marked as such"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").dependsOn("foo", "qix", "1.0").publish()

        file("settings.gradle") << """
            rootProject.name = 'fooProject'
        """

        file("build.gradle") << """
            apply plugin : 'project-report'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { compile }
            dependencies {
              compile 'foo:bar:1.0'
              compile 'grr:bzz:1.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")

        then:
        json.project.configurations[0].dependencies.size == 2
        json.project.configurations[0].dependencies[0].name == "foo:bar:1.0"
        json.project.configurations[0].dependencies[0].children[0].name == "foo:qix:1.0"
        json.project.configurations[0].dependencies[0].children[0].resolvable == false
        json.project.configurations[0].dependencies[1].name == "grr:bzz:1.0"
        json.project.configurations[0].dependencies[1].resolvable == false
    }

    def "conflictual dependencies are marked as such"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()
        mavenRepo.module("foo", "baz", "1.0").dependsOn("foo", "bar", "1.0").publish()

        file("build.gradle") << """
            apply plugin : 'project-report'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { compile }
            dependencies {
              compile 'foo:baz:1.0'
              compile 'foo:bar:2.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")

        then:
        json.project.configurations[0].dependencies.size == 2
        json.project.configurations[0].dependencies[0].name == "foo:baz:1.0"
        json.project.configurations[0].dependencies[0].children[0].name == "foo:bar:1.0 \u27A1 2.0"
        json.project.configurations[0].dependencies[0].children[0].hasConflict == true
        json.project.configurations[0].dependencies[1].name == "foo:bar:2.0"
    }

    def "generates report for multiple projects"() {
        given:
        file("settings.gradle") << """
            rootProject.name = 'fooProject'
            include 'a', 'b'
        """
        file("build.gradle") << """
            apply plugin : 'project-report'

            htmlDependencyReport {
                projects = [project(':a'), project(':b')]
            }
        """
        ["a", "b"].each { module ->
            file(module, "build.gradle") << """
                apply plugin: 'project-report'
            """
        }

        when:
        run "htmlDependencyReport"
        def jsonA = readGeneratedJson("root.a")
        def jsonB = readGeneratedJson("root.b")

        then:
        jsonA.project.name == "a"
        jsonB.project.name == "b"
    }

    def "copies necessary css, images and js files"() {
        given:
        file("build.gradle") << """
            apply plugin : 'project-report'
            configurations { compile }
        """

        when:
        run "htmlDependencyReport"

        then:
        file("build/reports/project/dependencies/d.gif").assertExists()
        file("build/reports/project/dependencies/d.png").assertExists()
        file("build/reports/project/dependencies/jquery.jstree.js").assertExists()
        file("build/reports/project/dependencies/jquery-1.10.1.min.js").assertExists()
        file("build/reports/project/dependencies/script.js").assertExists()
        file("build/reports/project/dependencies/style.css").assertExists()
        file("build/reports/project/dependencies/throbber.gif").assertExists()
        file("build/reports/project/dependencies/tree.css").assertExists()

        file("build/reports/project/dependencies/root.html").getText().contains('<script src="root.js" charset="utf-8"></script>');
        file("build/reports/project/dependencies/root.js").assertExists();
        file("build/reports/project/dependencies/index.html").getText().contains('<script src="index.js" charset="utf-8"></script>');
    }

    def "generates index.js file"() {
        given:
        file("settings.gradle") << """
            rootProject.name = 'fooProject'
            include 'a', 'b'
        """
        file("build.gradle") << """
            apply plugin : 'project-report'

            description = 'dummy description'

            htmlDependencyReport {
                projects = project.allprojects
            }
        """
        ["a", "b"].each { module ->
            file(module, "build.gradle") << """
                apply plugin: 'project-report'
            """
        }

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("index")

        then:
        json.generationDate != null
        json.gradleVersion != null

        json.projects.size() == 3
        json.projects[0].path == 'root:'
        json.projects[0].name == 'fooProject'
        json.projects[0].description == 'dummy description'
        json.projects[0].file == 'root.html'

        json.projects[1].path == 'root:a'
        json.projects[1].name == 'a'
        json.projects[1].description == null
        json.projects[1].file == 'root.a.html'

        json.projects[2].path == 'root:b'
        json.projects[2].name == 'b'
        json.projects[2].description == null
        json.projects[2].file == 'root.b.html'
    }

    def "renders insights graphs"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()
        mavenRepo.module("foo", "baz", "1.0").dependsOn("foo", "bar", "1.0").publish()

        file("build.gradle") << """
            apply plugin : 'project-report'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { compile }
            dependencies {
              compile 'foo:baz:1.0'
              compile 'foo:bar:2.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")

        then:
        json.project.configurations[0].moduleInsights.size == 2
        json.project.configurations[0].moduleInsights.any { it.module == 'foo:bar' }
        json.project.configurations[0].moduleInsights.any { it.module == 'foo:baz' }
        def barInsight = json.project.configurations[0].moduleInsights.find({ it.module == 'foo:bar' }).insight
        barInsight.size == 2
        barInsight[0].name == 'foo:bar:2.0'
        barInsight[0].resolvable == true
        barInsight[0].hasConflict == false
        barInsight[0].description == 'conflict resolution'
        barInsight[0].children.size == 1
        barInsight[0].children[0].name == 'compile'
        barInsight[0].children[0].resolvable == true
        barInsight[0].children[0].hasConflict == false
        barInsight[0].children[0].isLeaf == true
        barInsight[0].children[0].alreadyRendered == false
        barInsight[0].children[0].children.size == 0

        barInsight[1].name == "foo:bar:1.0 \u27A1 2.0"
        barInsight[1].resolvable == true
        barInsight[1].hasConflict == true
        barInsight[1].children.size == 1
        barInsight[1].children[0].name == 'foo:baz:1.0'
        barInsight[1].children[0].resolvable == true
        barInsight[1].children[0].isLeaf == false
        barInsight[1].children[0].alreadyRendered == false
        barInsight[1].children[0].children.size == 1
        barInsight[1].children[0].children[0].name == 'compile'
        barInsight[1].children[0].children[0].resolvable == true
        barInsight[1].children[0].children[0].hasConflict == false
        barInsight[1].children[0].children[0].isLeaf == true
        barInsight[1].children[0].children[0].alreadyRendered == false
        barInsight[1].children[0].children[0].children.size == 0

        def bazInsight = json.project.configurations[0].moduleInsights.find({ it.module == 'foo:baz' }).insight
        bazInsight.size == 1
        bazInsight[0].name == 'foo:baz:1.0'
        bazInsight[0].resolvable == true
        bazInsight[0].hasConflict == false
        bazInsight[0].children.size == 1
        bazInsight[0].children[0].name == 'compile'
        bazInsight[0].children[0].resolvable == true
        bazInsight[0].children[0].hasConflict == false
        bazInsight[0].children[0].isLeaf == true
        bazInsight[0].children[0].alreadyRendered == false
        bazInsight[0].children[0].children.size == 0
    }

    private def readGeneratedJson(fileNameWithoutExtension) {
        TestFile htmlReport = file("build/reports/project/dependencies/" + fileNameWithoutExtension + ".js")
        String content = htmlReport.getText(StandardCharsets.UTF_8.name());
        // remove the variable declaration
        int equalSignIndex = content.indexOf('=');
        String jsonAsString = content.substring(equalSignIndex + 1);
        // remove the trailing ;
        jsonAsString = jsonAsString.substring(0, jsonAsString.length() - 1);
        JsonSlurper json = new JsonSlurper()
        return json.parseText(jsonAsString)
    }
}