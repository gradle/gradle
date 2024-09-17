/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.plugins.quality.codenarc

import org.gradle.api.plugins.quality.CodeNarcPlugin
import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.configurationcache.isolated.IsolatedProjectsExecuterFixture

class CodeNarcPluginIntegrationTest extends WellBehavedPluginTest implements CodeNarcTestFixture, IsolatedProjectsExecuterFixture {
    @Override
    String getPluginName() {
        return "codenarc"
    }

    @Override
    String getMainTask() {
        return "check"
    }

    def setup() {
        writeBuildFile()
        writeConfigFile()
    }

    def "adds codenarc task for each source set"() {
        given:
        buildFile << '''
            sourceSets {
                other {
                    groovy
                }
            }
            def assertTaskConfiguration(taskName, sourceSet) {
                def task = project.tasks.findByName(taskName)
                assert task instanceof CodeNarc
                task.with {
                    assert description == "Run CodeNarc analysis for ${sourceSet.name} classes"
                    assert source as List == sourceSet.groovy  as List
                    assert codenarcClasspath.files == project.configurations.codenarc.files
                    assert config.inputFiles.singleFile == project.file("config/codenarc/codenarc.xml")
                    assert configFile == project.file("config/codenarc/codenarc.xml")
                    assert maxPriority1Violations.get() == 0
                    assert maxPriority2Violations.get() == 0
                    assert maxPriority3Violations.get() == 0
                    assert reports.enabled*.name == ["html"]
                    assert reports.html.outputLocation.asFile.get() == project.file("build/reports/codenarc/${sourceSet.name}.html")
                    assert ignoreFailures == false
                }
            }
            assertTaskConfiguration('codenarcMain', project.sourceSets.main)
            assertTaskConfiguration('codenarcTest', project.sourceSets.test)
            assertTaskConfiguration('codenarcOther', project.sourceSets.other)
        '''.stripIndent()

        expect:
        succeeds 'help'
    }

    def "adds codenarc tasks from each source sets to check lifecycle task"() {
        given:
        buildFile << '''
            sourceSets {
                other {
                    groovy
                }
            }
            task codenarcCustom(type: CodeNarc)
        '''.stripIndent()

        when:
        succeeds 'check'

        then:
        executed(":codenarcMain")
        executed(":codenarcTest")
        executed(":codenarcOther")
        notExecuted(":codenarcCustom")
    }

    def "can customize per-source-set tasks via extension"() {
        given:
        buildFile << '''
            sourceSets {
                other {
                    groovy
                }
            }
            codenarc {
                configFile = project.file("codenarc-config")
                maxPriority1Violations = 10
                maxPriority2Violations = 50
                maxPriority3Violations = 200
                reportFormat = "xml"
                reportsDir = project.file("codenarc-reports")
                ignoreFailures = true
            }
            def hasCustomizedSettings(taskName, sourceSet) {
                def task = project.tasks.findByName(taskName)
                assert task instanceof CodeNarc
                task.with {
                    assert description == "Run CodeNarc analysis for ${sourceSet.name} classes"
                    assert source as List == sourceSet.groovy as List
                    assert codenarcClasspath.files == project.configurations.codenarc.files
                    assert config.inputFiles.singleFile == project.file("codenarc-config")
                    assert configFile == project.file("codenarc-config")
                    assert maxPriority1Violations.get() == 10
                    assert maxPriority2Violations.get() == 50
                    assert maxPriority3Violations.get() == 200
                    assert reports.enabled*.name == ["xml"]
                    assert reports.xml.outputLocation.asFile.get() == project.file("codenarc-reports/${sourceSet.name}.xml")
                    assert ignoreFailures == true
                }
            }
            hasCustomizedSettings('codenarcMain', project.sourceSets.main)
            hasCustomizedSettings('codenarcTest', project.sourceSets.test)
            hasCustomizedSettings('codenarcOther', project.sourceSets.other)
        '''.stripIndent()

        expect:
        succeeds 'help'
    }

    def "can customize which tasks are added to check lifecycle task"() {
        given:
        buildFile << '''
            sourceSets {
                other {
                    groovy
                }
            }
            task codenarcCustom(type: CodeNarc)
            codenarc {
                sourceSets = [project.sourceSets.main]
            }
        '''.stripIndent()

        when:
        succeeds 'check'

        then:
        executed(':codenarcMain')
        notExecuted(':codenarcTest')
        notExecuted(':codenarcOther')
        notExecuted(':codenarcCustom')
    }

    def "can use legacy configFile extension property"() {
        given:
        buildFile << '''
            codenarc {
                configFile = project.file("codenarc-config")
            }
            assert project.codenarc.configFile == project.file("codenarc-config") // computed property
            assert project.tasks.codenarcMain.configFile == project.file("codenarc-config")
            assert project.tasks.codenarcTest.configFile == project.file("codenarc-config")
        '''.stripIndent()

        expect:
        succeeds 'help'
    }

    def "allows configuring tool dependencies explicitly via #method"(String method, String buildScriptSnippet) {
        expect: //defaults exist and can be inspected
        succeeds("dependencies", "--configuration", "codenarc")
        output.contains "org.codenarc:CodeNarc:${CodeNarcPlugin.DEFAULT_CODENARC_VERSION}"

        when:
        buildFile << buildScriptSnippet

        then:
        succeeds("dependencies", "--configuration", "codenarc")
        output.contains "org.codenarc:CodeNarc:0.17"
        !output.contains("FAILED")

        where:
        method         | buildScriptSnippet
        'dependencies' | "dependencies { codenarc 'org.codenarc:CodeNarc:0.17' }"
        'toolVersion'  | "codenarc { toolVersion '0.17' } "
    }

    def "codenarc runs successfully for a child project with isolated projects"() {
        given:
        settingsFile << "include 'child:grand'"
        writeBuildFile(file("child/grand/build.gradle"))
        file("child/grand/src/main/groovy/Dummy.groovy") << "Dummy {}"
        withIsolatedProjects()

        expect:
        succeeds(":child:grand:codenarcMain")
        report(file("child/grand"), "main").exists()
    }

    private void writeBuildFile() {
        writeBuildFile(buildFile)
    }

    private static void writeBuildFile(File buildFile) {
        buildFile << """
            apply plugin: "groovy"
            apply plugin: "codenarc"

            ${mavenCentralRepository()}

            dependencies {
                implementation localGroovy()
            }
        """.stripIndent()
    }

    private void writeConfigFile() {
        file("config/codenarc/codenarc.xml") << """
            <ruleset xmlns="http://codenarc.org/ruleset/1.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
                    xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
                <ruleset-ref path="rulesets/naming.xml"/>
            </ruleset>
        """.stripIndent()
    }
}
