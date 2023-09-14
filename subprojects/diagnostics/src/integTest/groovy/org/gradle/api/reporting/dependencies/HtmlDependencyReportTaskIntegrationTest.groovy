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
package org.gradle.api.reporting.dependencies

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.jsoup.Jsoup
import spock.lang.Issue

/**
 * Integration tests for the HTML dependency report generation task
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
        json.project.configurations.size() == 2
        json.project.configurations[0].name == "compile"
        json.project.configurations[0].dependencies.size() == 1
        json.project.configurations[0].dependencies[0].module == "foo:baz"
        json.project.configurations[0].dependencies[0].name == "foo:baz:1.0"
        json.project.configurations[0].dependencies[0].resolvable == 'RESOLVED'
        json.project.configurations[0].dependencies[0].alreadyRendered == false
        json.project.configurations[0].dependencies[0].hasConflict == false
        json.project.configurations[0].dependencies[0].children.size() == 2

        json.project.configurations[0].dependencies[0].children[0].module == "foo:bar"
        json.project.configurations[0].dependencies[0].children[0].name == "foo:bar:1.0"
        json.project.configurations[0].dependencies[0].children[0].resolvable == 'RESOLVED'
        json.project.configurations[0].dependencies[0].children[0].alreadyRendered == false
        json.project.configurations[0].dependencies[0].children[0].hasConflict == false
        json.project.configurations[0].dependencies[0].children[0].children.empty

        json.project.configurations[0].dependencies[0].children[1].module == "foo:qix"
        json.project.configurations[0].dependencies[0].children[1].name == "foo:qix:1.0"
        json.project.configurations[0].dependencies[0].children[1].resolvable == 'RESOLVED'
        json.project.configurations[0].dependencies[0].children[1].alreadyRendered == false
        json.project.configurations[0].dependencies[0].children[1].hasConflict == false
        json.project.configurations[0].dependencies[0].children[1].children.empty

        json.project.configurations[1].name == "testCompile"
        json.project.configurations[1].dependencies.size() == 1
        json.project.configurations[1].dependencies[0].module == "foo:bar"
        json.project.configurations[1].dependencies[0].name == "foo:bar:1.0"
        json.project.configurations[1].dependencies[0].resolvable == 'RESOLVED'
        json.project.configurations[1].dependencies[0].alreadyRendered == false
        json.project.configurations[1].dependencies[0].hasConflict == false
        json.project.configurations[1].dependencies[0].children.empty
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
        json.project.configurations[0].dependencies.size() == 2
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
        json.project.configurations[0].dependencies.size() == 2
        json.project.configurations[0].dependencies[0].name == "foo:bar:1.0"
        json.project.configurations[0].dependencies[0].children[0].name == "foo:qix:1.0"
        json.project.configurations[0].dependencies[0].children[0].resolvable == 'FAILED'
        json.project.configurations[0].dependencies[1].name == "grr:bzz:1.0"
        json.project.configurations[0].dependencies[1].resolvable == 'FAILED'
    }

    def "conflicting dependencies are marked as such"() {
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
        json.project.configurations[0].dependencies.size() == 2
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
        file("build/reports/project/dependencies/images/d.gif").assertExists()
        file("build/reports/project/dependencies/images/d.png").assertExists()
        file("build/reports/project/dependencies/js/jquery.jstree.js").assertExists()
        file("build/reports/project/dependencies/js/jquery.min-3.5.1.js").assertExists()
        file("build/reports/project/dependencies/js/script.js").assertExists()
        file("build/reports/project/dependencies/css/style.css").assertExists()
        file("build/reports/project/dependencies/images/throbber.gif").assertExists()
        file("build/reports/project/dependencies/css/tree.css").assertExists()

        file("build/reports/project/dependencies/root.html").getText().contains('<script src="root.js" charset="utf-8">');
        file("build/reports/project/dependencies/root.js").assertExists();
    }

    def "generates index.html file"() {
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

        when:
        run "htmlDependencyReport"
        def html = readGeneratedHtml("index")

        then:
        def rows = html.select("table tbody tr")
        rows.size() == 3

        def rootProject = rows[0].select("td")
        rootProject[0].text() == "root project 'fooProject'"
        rootProject[1].text() == "dummy description"

        def projectA = rows[1].select("td")
        projectA[0].text() == "project ':a'"
        projectA[1].text() == ""

        def projectB = rows[2].select("td")
        projectB[0].text() == "project ':b'"
        projectB[1].text() == ""
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
        json.project.configurations[0].moduleInsights.size() == 2
        json.project.configurations[0].moduleInsights.any { it.module == 'foo:bar' }
        json.project.configurations[0].moduleInsights.any { it.module == 'foo:baz' }
        def barInsight = json.project.configurations[0].moduleInsights.find({ it.module == 'foo:bar' }).insight
        barInsight.size() == 3
        barInsight[0].name == 'foo:bar:2.0'
        barInsight[0].resolvable == 'RESOLVED'
        barInsight[0].hasConflict == false
        barInsight[0].description == null
        barInsight[0].children.size() == 0

        barInsight[1].name == 'foo:bar:2.0'
        barInsight[1].resolvable == 'RESOLVED'
        barInsight[1].hasConflict == false
        barInsight[1].description == null
        barInsight[1].children.size() == 1
        barInsight[1].children[0].name == 'compile'
        barInsight[1].children[0].resolvable == 'RESOLVED'
        barInsight[1].children[0].hasConflict == false
        barInsight[1].children[0].isLeaf == true
        barInsight[1].children[0].alreadyRendered == false
        barInsight[1].children[0].children.size() == 0

        barInsight[2].name == "foo:bar:1.0 \u27A1 2.0"
        barInsight[2].resolvable == 'RESOLVED'
        barInsight[2].hasConflict == true
        barInsight[2].children.size() == 1
        barInsight[2].children[0].name == 'foo:baz:1.0'
        barInsight[2].children[0].resolvable == 'RESOLVED'
        barInsight[2].children[0].isLeaf == false
        barInsight[2].children[0].alreadyRendered == false
        barInsight[2].children[0].children.size() == 1
        barInsight[2].children[0].children[0].name == 'compile'
        barInsight[2].children[0].children[0].resolvable == 'RESOLVED'
        barInsight[2].children[0].children[0].hasConflict == false
        barInsight[2].children[0].children[0].isLeaf == true
        barInsight[2].children[0].children[0].alreadyRendered == false
        barInsight[2].children[0].children[0].children.size() == 0

        def bazInsight = json.project.configurations[0].moduleInsights.find({ it.module == 'foo:baz' }).insight
        bazInsight.size() == 2
        bazInsight[0].name == 'foo:baz:1.0'
        bazInsight[0].resolvable == 'RESOLVED'
        bazInsight[0].hasConflict == false
        bazInsight[0].children.size() == 0

        bazInsight[1].name == 'foo:baz:1.0'
        bazInsight[1].resolvable == 'RESOLVED'
        bazInsight[1].hasConflict == false
        bazInsight[1].children.size() == 1
        bazInsight[1].children[0].name == 'compile'
        bazInsight[1].children[0].resolvable == 'RESOLVED'
        bazInsight[1].children[0].hasConflict == false
        bazInsight[1].children[0].isLeaf == true
        bazInsight[1].children[0].alreadyRendered == false
        bazInsight[1].children[0].children.empty
    }

    def "doesn't add insight for dependency with same prefix"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "barImpl", "1.0").publish()

        file("build.gradle") << """
            apply plugin : 'project-report'
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations { compile }
            dependencies {
              compile 'foo:bar:1.0'
              compile 'foo:barImpl:1.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")

        then:
        def barInsight = json.project.configurations[0].moduleInsights.find({ it.module == 'foo:bar' }).insight
        barInsight.size() == 2
        barInsight[0].name == 'foo:bar:1.0'
        barInsight[1].name == 'foo:bar:1.0'
    }

    @Issue("GRADLE-2979")
    def "renders a mix of project and external dependencies"() {
        given:
        mavenRepo.module("foo", "bar", "1.0").publish()
        mavenRepo.module("foo", "bar", "2.0").publish()

        file("settings.gradle") << """include 'a', 'b', 'a:c', 'd', 'e'
rootProject.name = 'root'
"""

        file("build.gradle") << """
            apply plugin : 'project-report'

            allprojects {
                version = '1.0'
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                configurations {
                    api
                    compileClasspath.extendsFrom api
                    "default" { extendsFrom compileClasspath }
                }
            }

            project(":a") {
               dependencies {
                    api 'foo:bar:1.0'
                }
            }

            project(":b") {
               dependencies {
                    api 'foo:bar:0.5.dont.exist'
                }
            }

            project(":a:c") {
               dependencies {
                    api 'foo:bar:2.0'
               }
            }

            project(":d") {
               dependencies {
                    api project(":e")
                }
            }

            project(":e") {
               dependencies {
                    api 'foo:bar:2.0'
                }
            }

            dependencies {
                api project(":a"), project(":b"), project(":a:c"), project(":d")
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")
        def compileClasspathConfiguration = json.project.configurations.find { it.name == "compileClasspath" }

        then:
        compileClasspathConfiguration
        compileClasspathConfiguration.dependencies.size() == 4
        compileClasspathConfiguration.dependencies[0].module == null
        compileClasspathConfiguration.dependencies[0].name == "project :a"
        compileClasspathConfiguration.dependencies[0].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[0].alreadyRendered == false
        compileClasspathConfiguration.dependencies[0].hasConflict == false
        compileClasspathConfiguration.dependencies[0].children.size() == 1
        compileClasspathConfiguration.dependencies[0].children[0].module == "foo:bar"
        compileClasspathConfiguration.dependencies[0].children[0].name == "foo:bar:1.0 \u27A1 2.0"
        compileClasspathConfiguration.dependencies[0].children[0].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[0].children[0].alreadyRendered == false
        compileClasspathConfiguration.dependencies[0].children[0].hasConflict == true
        compileClasspathConfiguration.dependencies[0].children[0].children.empty

        compileClasspathConfiguration.dependencies[1].module == null
        compileClasspathConfiguration.dependencies[1].name == "project :b"
        compileClasspathConfiguration.dependencies[1].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[1].alreadyRendered == false
        compileClasspathConfiguration.dependencies[1].hasConflict == false
        compileClasspathConfiguration.dependencies[1].children.size() == 1
        compileClasspathConfiguration.dependencies[1].children[0].module == "foo:bar"
        compileClasspathConfiguration.dependencies[1].children[0].name == "foo:bar:0.5.dont.exist \u27A1 2.0"
        compileClasspathConfiguration.dependencies[1].children[0].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[1].children[0].alreadyRendered == false
        compileClasspathConfiguration.dependencies[1].children[0].hasConflict == true
        compileClasspathConfiguration.dependencies[1].children[0].children.empty

        compileClasspathConfiguration.dependencies[2].module == null
        compileClasspathConfiguration.dependencies[2].name == "project :a:c"
        compileClasspathConfiguration.dependencies[2].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[2].alreadyRendered == false
        compileClasspathConfiguration.dependencies[2].hasConflict == false
        compileClasspathConfiguration.dependencies[2].children.size() == 1
        compileClasspathConfiguration.dependencies[2].children[0].module == "foo:bar"
        compileClasspathConfiguration.dependencies[2].children[0].name == "foo:bar:2.0"
        compileClasspathConfiguration.dependencies[2].children[0].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[2].children[0].alreadyRendered == false
        compileClasspathConfiguration.dependencies[2].children[0].hasConflict == false
        compileClasspathConfiguration.dependencies[2].children[0].children.empty

        compileClasspathConfiguration.dependencies[3].module == null
        compileClasspathConfiguration.dependencies[3].name == "project :d"
        compileClasspathConfiguration.dependencies[3].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[3].alreadyRendered == false
        compileClasspathConfiguration.dependencies[3].hasConflict == false
        compileClasspathConfiguration.dependencies[3].children.size() == 1
        compileClasspathConfiguration.dependencies[3].children[0].module == null
        compileClasspathConfiguration.dependencies[3].children[0].name == "project :e"
        compileClasspathConfiguration.dependencies[3].children[0].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[3].children[0].alreadyRendered == false
        compileClasspathConfiguration.dependencies[3].children[0].hasConflict == false
        compileClasspathConfiguration.dependencies[3].children[0].children.size() == 1
        compileClasspathConfiguration.dependencies[3].children[0].children[0].module == "foo:bar"
        compileClasspathConfiguration.dependencies[3].children[0].children[0].name == "foo:bar:2.0"
        compileClasspathConfiguration.dependencies[3].children[0].children[0].resolvable == 'RESOLVED'
        compileClasspathConfiguration.dependencies[3].children[0].children[0].alreadyRendered == false
        compileClasspathConfiguration.dependencies[3].children[0].children[0].hasConflict == false
        compileClasspathConfiguration.dependencies[3].children[0].children[0].children.empty
    }

    void "does not fail if a configuration is not resolvable"() {
        mavenRepo.module("foo", "foo", '1.0').publish()
        mavenRepo.module("foo", "bar", '2.0').publish()

        file("build.gradle") << """
            apply plugin : 'project-report'

            repositories {
               maven { url "${mavenRepo.uri}" }
            }
            configurations {
                api.canBeConsumed = false
                api.canBeResolved = false
                compile.extendsFrom api
            }
            dependencies {
                api 'foo:foo:1.0'
                compile 'foo:bar:2.0'
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")
        def apiConfiguration = json.project.configurations.find { it.name == "api" }

        then:
        apiConfiguration
        apiConfiguration.dependencies.size() == 1
        apiConfiguration.dependencies[0].name == "foo:foo:1.0"
        apiConfiguration.dependencies[0].resolvable == 'UNRESOLVED'
        apiConfiguration.dependencies[0].alreadyRendered == false
        apiConfiguration.dependencies[0].hasConflict == false
        apiConfiguration.dependencies[0].children.empty
    }

    void "treats a configuration that is deprecated for resolving as not resolvable"() {
        mavenRepo.module("foo", "foo", '1.0').publish()

        file("build.gradle") << """
            apply plugin : 'project-report'

            repositories {
               maven { url "${mavenRepo.uri}" }
            }
            configurations {
                migratingUnlocked('compileOnly', org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE)
            }
            dependencies {
                compileOnly 'foo:foo:1.0'
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("The compileOnly configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 9.0. Please use another configuration instead. For more information, please refer to https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:deprecated-configurations in the Gradle documentation.")
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")
        def apiConfiguration = json.project.configurations.find { it.name == "compileOnly" }

        then:
        apiConfiguration
        apiConfiguration.dependencies.size() == 1
        apiConfiguration.dependencies[0].name == "foo:foo:1.0"
        apiConfiguration.dependencies[0].resolvable == 'UNRESOLVED'
        apiConfiguration.dependencies[0].alreadyRendered == false
        apiConfiguration.dependencies[0].hasConflict == false
        apiConfiguration.dependencies[0].children.empty
    }

    void "excludes directly undeclarable configurations"() {
        mavenRepo.module("foo", "foo", '1.0').publish()

        file("build.gradle") << """
            apply plugin : 'project-report'

            repositories {
               maven { url "${mavenRepo.uri}" }
            }
            configurations {
                undeclarable {
                    canBeDeclared = false
                }
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")
        def undeclarableConfiguration = json.project.configurations.find { it.name == "undeclarable" }

        then:
        !undeclarableConfiguration
    }

    void "includes indirectly declarable configurations"() {
        mavenRepo.module("foo", "foo", '1.0').publish()

        file("build.gradle") << """
            apply plugin : 'project-report'

            repositories {
               maven { url "${mavenRepo.uri}" }
            }
            configurations {
                declarable {
                    canBeDeclared = true
                }
                undeclarable {
                    canBeDeclared = false
                    extendsFrom(declarable)
                }
            }
        """

        when:
        run "htmlDependencyReport"
        def json = readGeneratedJson("root")
        def undeclarableConfiguration = json.project.configurations.find { it.name == "undeclarable" }

        then:
        undeclarableConfiguration
    }

    private def readGeneratedJson(fileNameWithoutExtension) {
        TestFile htmlReport = file("build/reports/project/dependencies/" + fileNameWithoutExtension + ".js")
        String content = htmlReport.getText("utf-8");
        // remove the variable declaration
        int equalSignIndex = content.indexOf('=');
        String jsonAsString = content.substring(equalSignIndex + 1);
        // remove the trailing ;
        jsonAsString = jsonAsString.substring(0, jsonAsString.length() - 1);
        JsonSlurper json = new JsonSlurper()
        return json.parseText(jsonAsString)
    }

    private def readGeneratedHtml(fileNameWithoutExtension) {
        TestFile htmlReport = file("build/reports/project/dependencies/" + fileNameWithoutExtension + ".html")
        return Jsoup.parse(htmlReport, "utf-8")
    }
}
