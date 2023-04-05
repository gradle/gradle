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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.quality.integtest.fixtures.CodeNarcCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.ToBeImplemented
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.hamcrest.CoreMatchers.startsWith

@TargetCoverage({ CodeNarcCoverage.supportedVersionsByCurrentJdk })
@Requires(TestPrecondition.STABLE_GROOVY)
class CodeNarcPluginVersionIntegrationTest extends MultiVersionIntegrationSpec implements CodeNarcTestFixture {
    def setup() {
        buildFile << """
            apply plugin: "groovy"
            apply plugin: "codenarc"

            ${mavenCentralRepository()}

            codenarc {
                toolVersion = '${version}'
            }

            dependencies {
                implementation localGroovy()
            }

            ${JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_14) ?
            """
            configurations.codenarc {
                resolutionStrategy.force 'org.codehaus.groovy:groovy:${GroovyCoverage.MINIMAL_GROOVY_3}' // force latest Groovy 3 when using Java 14+.  Do not use GroovySystem#version as Groovy 4 needs different coordinates
            }
            """ : ""}
        """.stripIndent()

        writeRuleFile()
    }

    def "analyze good code"() {
        goodCode()

        expect:
        succeeds("check")
        report("main").exists()
        report("test").exists()
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "is incremental"() {
        given:
        goodCode()

        expect:
        succeeds("codenarcMain")
        executedAndNotSkipped(":codenarcMain")

        succeeds(":codenarcMain")
        skipped(":codenarcMain")

        when:
        report("main").delete()

        then:
        succeeds("codenarcMain")
        executedAndNotSkipped(":codenarcMain")
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "can generate multiple reports"() {
        given:
        buildFile << """
            codenarcMain.reports {
                xml.required = true
                text.required = true
            }
        """

        and:
        goodCode()

        expect:
        succeeds("check")
        executedAndNotSkipped(":codenarcMain")
        ["html", "xml", "txt"].each {
            assert report("main", it).exists()
        }
    }

    def "analyze bad code"() {
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':codenarcTest'.")
        failure.assertThatCause(startsWith("CodeNarc rule violations were found. See the report at:"))
        failure.assertHasResolutions(SCAN)
        !report("main").text.contains("Class2")
        report("test").text.contains("testclass2")
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
        !report("main").text.contains("Class2")
        report("test").text.contains("testclass2")

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
        report("test").text.contains("testclass2")
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

    def "output should be printed in stdout if console type is specified"() {
        when:
        buildFile << '''
            codenarc {
                configFile == file('config/codenarc/codenarc.xml')
                reportFormat = 'console'
            }
        '''
        file('src/main/groovy/a/A.groovy') << 'package a;class A{}'

        then:
        succeeds('check')
        output.contains('CodeNarc Report')
        output.contains('CodeNarc completed: (p1=0; p2=0; p3=0)')
    }

    @Issue("https://github.com/gradle/gradle/issues/2326")
    @ToBeImplemented
    def "check task should not be up-to-date after clean if console type is specified"() {
        given:
        buildFile << '''
            codenarc {
                configFile == file('config/codenarc/codenarc.xml')
                reportFormat = 'console'
            }
        '''
        file('src/main/groovy/a/A.groovy') << 'package a;class A{}'

        when:
        succeeds('check')
        succeeds('clean', 'check')

        then:
        // TODO These should match
        !!!skipped(':codenarcMain')
        !!!output.contains('CodeNarc Report')
        !!!output.contains('CodeNarc completed: (p1=0; p2=0; p3=0)')
    }
}
