/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.hamcrest.Matchers.startsWith

class CodeNarcPluginIntegrationTest extends WellBehavedPluginTest {
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
                    assert source as List == sourceSet.allGroovy  as List
                    assert codenarcClasspath == project.configurations.codenarc
                    assert config.inputFiles.singleFile == project.file("config/codenarc/codenarc.xml")
                    assert configFile == project.file("config/codenarc/codenarc.xml")
                    assert maxPriority1Violations == 0
                    assert maxPriority2Violations == 0
                    assert maxPriority3Violations == 0
                    assert reports.enabled*.name == ["html"]
                    assert reports.html.destination == project.file("build/reports/codenarc/${sourceSet.name}.html")
                    assert ignoreFailures == false
                }
            }
            task assertTaskForEachSourceSet {
                doLast {
                    assertTaskConfiguration('codenarcMain', project.sourceSets.main)
                    assertTaskConfiguration('codenarcTest', project.sourceSets.test)
                    assertTaskConfiguration('codenarcOther', project.sourceSets.other)
                }
            }
        '''.stripIndent()

        expect:
        succeeds 'assertTaskForEachSourceSet'
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
        ":codenarcMain" in executedTasks
        ":codenarcTest" in executedTasks
        ":codenarcOther" in executedTasks
        !(":codenarcCustom" in executedTasks)
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
                    assert source as List == sourceSet.allGroovy as List
                    assert codenarcClasspath == project.configurations.codenarc
                    assert config.inputFiles.singleFile == project.file("codenarc-config")
                    assert configFile == project.file("codenarc-config")
                    assert maxPriority1Violations == 10
                    assert maxPriority2Violations == 50
                    assert maxPriority3Violations == 200
                    assert reports.enabled*.name == ["xml"]
                    assert reports.xml.destination == project.file("codenarc-reports/${sourceSet.name}.xml")
                    assert ignoreFailures == true
                }
            }
            task assertHasCustomizedSettings {
                doLast {
                    hasCustomizedSettings('codenarcMain', project.sourceSets.main)
                    hasCustomizedSettings('codenarcTest', project.sourceSets.test)
                    hasCustomizedSettings('codenarcOther', project.sourceSets.other)
                }
            }
        '''.stripIndent()

        expect:
        succeeds 'assertHasCustomizedSettings'
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
        ':codenarcMain' in executedTasks
        !(':codenarcTest' in executedTasks)
        !(':codenarcOther' in executedTasks)
        !(':codenarcCustom' in executedTasks)
    }

    def "can use legacy configFile extension property"() {
        given:
        buildFile << '''
            codenarc {
                configFile = project.file("codenarc-config")
            }
            task assertCodeNarcConfiguration {
                doLast {
                    assert project.codenarc.configFile == project.file("codenarc-config") // computed property
                    assert project.tasks.codenarcMain.configFile == project.file("codenarc-config")
                    assert project.tasks.codenarcTest.configFile == project.file("codenarc-config")
                }
            }
        '''.stripIndent()

        expect:
        succeeds 'assertCodeNarcConfiguration'
    }

    def "allows configuring tool dependencies explicitly"() {
        expect: //defaults exist and can be inspected
        succeeds("dependencies", "--configuration", "codenarc")
        output.contains "org.codenarc:CodeNarc:"

        when:
        buildFile << """
            dependencies {
                //downgrade version:
                codenarc "org.codenarc:CodeNarc:0.17"
            }
        """

        then:
        succeeds("dependencies", "--configuration", "codenarc")
        output.contains "org.codenarc:CodeNarc:0.17"
    }

    def "analyze good code"() {
        goodCode()

        expect:
        succeeds("check")
        file("build/reports/codenarc/main.html").exists()
        file("build/reports/codenarc/test.html").exists()
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "is incremental"() {
        given:
        goodCode()

        expect:
        succeeds("codenarcMain") && ":codenarcMain" in nonSkippedTasks
        succeeds(":codenarcMain") && ":codenarcMain" in skippedTasks

        when:
        file("build/reports/codenarc/main.html").delete()

        then:
        succeeds("codenarcMain") && ":codenarcMain" in nonSkippedTasks
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "can generate multiple reports"() {
        given:
        buildFile << """
            codenarcMain.reports {
                xml.enabled true
                text.enabled true
            }
        """

        and:
        goodCode()

        expect:
        succeeds("check")
        ":codenarcMain" in nonSkippedTasks
        file("build/reports/codenarc/main.html").exists()
        file("build/reports/codenarc/main.xml").exists()
        file("build/reports/codenarc/main.txt").exists()
    }

    def "analyze bad code"() {
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':codenarcTest'.")
        failure.assertThatCause(startsWith("CodeNarc rule violations were found. See the report at:"))
        !file("build/reports/codenarc/main.html").text.contains("Class2")
        file("build/reports/codenarc/test.html").text.contains("testclass2")
    }

    def "can ignore failures"() {
        badCode()
        buildFile << """
            codenarc {
                ignoreFailures = true
            }
        """

        expect:
        succeeds("check")
        output.contains("CodeNarc rule violations were found. See the report at:")
        !file("build/reports/codenarc/main.html").text.contains("Class2")
        file("build/reports/codenarc/test.html").text.contains("testclass2")

    }

    def "can configure max violations"() {
        badCode()
        buildFile << """
            codenarcTest {
                maxPriority2Violations = 1
            }
        """

        expect:
        succeeds("check")
        !output.contains("CodeNarc rule violations were found. See the report at:")
        file("build/reports/codenarc/test.html").text.contains("testclass2")
    }

    @Issue("GRADLE-3492")
    def "can exclude code"() {
        badCode()
        buildFile << """
            codenarcMain {
                exclude '**/class1*'
                exclude '**/Class2*'
            }
            codenarcTest {
                exclude '**/TestClass1*'
                exclude '**/testclass2*'
            }
        """.stripIndent()

        expect:
        succeeds("check")
    }

    private goodCode() {
        file("src/main/groovy/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/test/groovy/org/gradle/testclass1.java") << "package org.gradle; class testclass1 { }"
        file("src/main/groovy/org/gradle/Class2.groovy") << "package org.gradle; class Class2 { }"
        file("src/test/groovy/org/gradle/TestClass2.groovy") << "package org.gradle; class TestClass2 { }"
    }

    private badCode() {
        file("src/main/groovy/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/main/groovy/org/gradle/Class2.groovy") << "package org.gradle; class Class2 { }"
        file("src/test/groovy/org/gradle/TestClass1.java") << "package org.gradle; class TestClass1 { }"
        file("src/test/groovy/org/gradle/testclass2.groovy") << "package org.gradle; class testclass2 { }"
    }

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "groovy"
apply plugin: "codenarc"

repositories {
    mavenCentral()
}

dependencies {
    compile localGroovy()
}
        """
    }

    private void writeConfigFile() {
        file("config/codenarc/codenarc.xml") << """
<ruleset xmlns="http://codenarc.org/ruleset/1.0"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
        xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">
    <ruleset-ref path="rulesets/naming.xml"/>
</ruleset>
        """
    }
}
