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

package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.quality.integtest.fixtures.CheckstyleCoverage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.Matchers
import org.gradle.util.internal.Resources
import org.gradle.util.internal.ToBeImplemented
import org.gradle.util.internal.VersionNumber
import org.hamcrest.Matcher
import org.junit.Assume
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.Matchers.containsText
import static org.gradle.util.internal.TextUtil.normaliseFileSeparators
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

@TargetCoverage({ CheckstyleCoverage.getSupportedVersionsByJdk() })
class CheckstylePluginVersionIntegrationTest extends MultiVersionIntegrationSpec {
    @Rule
    public final Resources resources = new Resources()

    def setup() {
        writeBuildFile()
        writeConfigFile()
    }

    def "analyze good code"() {
        goodCode()

        expect:
        succeeds('check')
        file("build/reports/checkstyle/main.sarif").assertDoesNotExist()
        file("build/reports/checkstyle/test.sarif").assertDoesNotExist()
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class2"))
        file("build/reports/checkstyle/test.xml").assertContents(containsClass("org.gradle.TestClass1"))
        file("build/reports/checkstyle/test.xml").assertContents(containsClass("org.gradle.TestClass2"))

        file("build/reports/checkstyle/main.html").assertExists()
        file("build/reports/checkstyle/main.html").assertContents(containsText("No violations were found."))
    }

    def "supports fallback when configDirectory does not exist"() {
        goodCode()
        buildFile << """
            checkstyle {
                config = project.resources.text.fromString('''<!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
                        "https://www.puppycrawl.com/dtds/configuration_1_3.dtd">
                <module name="Checker">

                    <module name="FileTabCharacter"/>

                    <module name="SuppressionFilter">
                        <property name="file" value="\${config_loc}/suppressions.xml" default=""/>
                        <property name="optional" value="true"/>
                    </module>
                </module>''')

                configDirectory = file("config/does-not-exist")
            }
        """

        expect:
        succeeds('check')
    }

    def "changes to files in config dir causes task to be out-of-date"() {
        goodCode()
        when:
        succeeds('check')
        then:
        result.assertTaskExecuted(":checkstyleMain")

        when:
        succeeds('check')
        then:
        result.assertTaskSkipped(":checkstyleMain")

        when:
        file("config/checkstyle/new-file.xml").touch()
        succeeds('check')
        then:
        result.assertTaskNotSkipped(":checkstyleMain")
    }

    @ToBeImplemented
    @Issue("GRADLE-3432")
    def "analyze bad resources"() {
        defaultLanguage('en')
        writeConfigFileForResources()
        badResources()

        expect:
        // TODO Should fail
        succeeds('check')

        // TODO These should match
        // file("build/reports/checkstyle/main.xml").assertContents(containsLine(containsString("bad.properties")))
        // file("build/reports/checkstyle/main.html").assertContents(containsLine(containsString("bad.properties")))
    }

    def "analyze bad code"() {
        defaultLanguage('en')
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertThatCause(startsWith("Checkstyle rule violations were found. See the report at:"))
        failure.assertHasErrorOutput("Name 'class1' must match pattern")
        file("build/reports/checkstyle/main.sarif").assertDoesNotExist()
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    @Issue("https://github.com/gradle/gradle/issues/12270")
    def "can analyse a single source file"() {
        buildFile << """
            checkstyleMain.source = ['src/main/java/org/gradle/Class1.java']
        """
        goodCode()

        expect:
        succeeds('check')
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class1"))
    }

    def "can suppress console output"() {
        def message = "Name 'class1' must match pattern"

        given:
        defaultLanguage('en')
        badCode()
        fails("check")
        failure.assertHasErrorOutput(message)

        when:
        buildFile << "checkstyle { showViolations = false }"
        fails("check")

        then:
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertThatCause(startsWith("Checkstyle rule violations were found. See the report at:"))
        failure.assertNotOutput(message)
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    def "can ignore failures"() {
        badCode()
        buildFile << """
            checkstyle {
                ignoreFailures = true
            }
        """

        expect:
        succeeds("check")
        // Issue #881:
        // Checkstyle violations are reported even when build passing with ignoreFailures
        output.contains("Checkstyle rule violations were found. See the report at:")
        output.contains("Checkstyle files with violations: 2")
        output.contains("Checkstyle violations by severity: [error:2]")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    def "can ignore maximum number of errors"() {
        badCode()
        buildFile << """
            checkstyle {
                maxErrors = 2
            }
        """

        expect:
        succeeds("check")
        // Issue #881:
        // Checkstyle violations are reported even when build passing due to error/warning thresholds
        output.contains("Checkstyle rule violations were found. See the report at:")
        output.contains("Checkstyle files with violations: 2")
        output.contains("Checkstyle violations by severity: [error:2]")

        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    def "can fail on maximum number of warnings"() {
        given:
        writeConfigFileWithWarnings()
        badCode()

        when:
        buildFile << """
            checkstyle {
                maxWarnings = 1
            }
        """

        then:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertThatCause(startsWith("Checkstyle rule violations were found. See the report at:"))
        failure.assertThatCause(Matchers.containsText("Checkstyle files with violations: 2"))
        failure.assertThatCause(Matchers.containsText("Checkstyle violations by severity: [warning:2]"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "is incremental"() {
        given:
        goodCode()

        expect:
        succeeds("checkstyleMain")
        executedAndNotSkipped(":checkstyleMain")

        executer.withArgument("-i")
        succeeds("checkstyleMain")
        skipped(":checkstyleMain")

        when:
        file("build/reports/checkstyle/main.xml").delete()
        file("build/reports/checkstyle/main.html").delete()

        then:
        succeeds("checkstyleMain")
        executedAndNotSkipped(":checkstyleMain")
    }

    def "can configure sarif reporting"() {
        given:
        Assume.assumeTrue(isSarifSupported())
        goodCode()

        when:
        buildFile << """
            checkstyleMain.reports {
                sarif {
                    required = true
                    sarif.outputLocation = file("baz.sarif")
                }
            }
        """

        then:
        succeeds "checkstyleMain"
        file("baz.sarif").exists()
    }

    def "can configure reporting"() {
        given:
        goodCode()

        when:
        buildFile << """
            checkstyleMain.reports {
                xml.outputLocation = file("foo.xml")
                html.outputLocation = file("bar.html")
            }
        """

        then:
        succeeds "checkstyleMain"
        file("foo.xml").exists()
        file("bar.html").exists()
    }

    def "can configure the html report with a custom stylesheet"() {
        given:
        goodCode()

        when:
        buildFile << """
            checkstyleMain.reports {
                html.required = true
                html.stylesheet resources.text.fromFile('${sampleStylesheet()}')
            }
        """

        then:
        succeeds "checkstyleMain"
        file("build/reports/checkstyle/main.html").exists()
        file("build/reports/checkstyle/main.html").assertContents(containsString("A custom Checkstyle stylesheet"))
    }

    @Issue("GRADLE-3490")
    def "do not output XML report when only HTML report is enabled"() {
        given:
        goodCode()
        buildFile << '''
            tasks.withType(Checkstyle) {
                reports {
                    xml.required = false
                    html.required = true
                }
            }
        '''.stripIndent()

        when:
        succeeds 'checkstyleMain'

        then:
        file("build/reports/checkstyle/main.html").exists()
        !file("build/reports/checkstyle/main.xml").exists()
        !file("build/tmp/checkstyleMain/main.xml").exists()
    }

    def "output SARIF report only when SARIF report is enabled"() {
        given:
        Assume.assumeTrue(isSarifSupported())
        goodCode()
        buildFile << '''
            tasks.withType(Checkstyle) {
                reports {
                    xml.required = false
                    html.required = false
                    sarif.required = true
                }
            }
        '''.stripIndent()

        when:
        succeeds 'checkstyleMain'

        then:
        file("build/reports/checkstyle/main.sarif").exists()
        !file("build/reports/checkstyle/main.xml").exists()
        !file("build/tmp/checkstyleMain/main.xml").exists()
        !file("build/reports/checkstyle/main.html").exists()
        !file("build/tmp/checkstyleMain/main.html").exists()
    }

    def "fails when SARIF enabled on unsupported checkstyle versions"() {
        given:
        Assume.assumeFalse(isSarifSupported())
        goodCode()
        buildFile << '''
            tasks.withType(Checkstyle) {
                reports {
                    xml.required = false
                    html.required = false
                    sarif.required = true
                }
            }
        '''.stripIndent()

        when:
        fails 'checkstyleMain'

        then:
        executedAndNotSkipped(":checkstyleMain")
        result.assertHasErrorOutput("SARIF report format is supported on Checkstyle versions 10.3.3 and newer. Please upgrade from Checkstyle $versionNumber or disable the SARIF format.")
    }

    def "changes to files in configDirectory make the task out-of-date"() {
        given:
        goodCode()
        succeeds "checkstyleMain"
        when:
        succeeds "checkstyleMain"
        then:
        skipped(":checkstyleMain")

        when:
        file("config/checkstyle/suppressions.xml") << "<!-- This is a change -->"
        and:
        succeeds "checkstyleMain"
        then:
        executedAndNotSkipped(":checkstyleMain")
    }

    def "can change built-in config_loc"() {
        given:
        goodCode()
        def suppressionsXml = file("config/checkstyle/suppressions.xml")
        suppressionsXml.moveToDirectory(file("custom"))

        buildFile << """
            checkstyle {
                configFile = file("config/checkstyle/checkstyle.xml")
                configDirectory = file("custom")
            }
        """
        when:
        succeeds "checkstyleMain"
        then:
        suppressionsXml.assertDoesNotExist()
        executedAndNotSkipped(":checkstyleMain")

        when:
        file("config/checkstyle/newFile.xml") << "<!-- This is a new file -->"
        and:
        succeeds "checkstyleMain"
        then:
        skipped(":checkstyleMain")
    }

    def "fails when config_loc is set"() {
        given:
        goodCode()

        buildFile << """
            checkstyle {
                configProperties['config_loc'] = file("custom")
            }
        """
        when:
        fails "checkstyleMain"

        then:
        executedAndNotSkipped(":checkstyleMain")
        result.assertHasErrorOutput("Cannot add config_loc to checkstyle.configProperties. Please configure the configDirectory on the checkstyle task instead.")
    }

    @Issue("https://github.com/gradle/gradle/issues/2326")
    def "check task should not be up-to-date after clean if it only outputs to console"() {
        given:
        defaultLanguage('en')
        writeConfigFileWithWarnings()
        badCode()
        buildFile << """
            tasks.withType(Checkstyle) {
                reports {
                    html.required = false
                    xml.required = false
                    sarif.required = false
                }
            }
        """

        when:
        succeeds('check')
        succeeds('clean', 'check')

        then:
        executedAndNotSkipped(':checkstyleMain')
        result.hasErrorOutput("[ant:checkstyle] [WARN]") || result.hasErrorOutput("warning: Name 'class1' must match pattern")
    }

    private static isSarifSupported() {
        return versionNumber >= VersionNumber.parse("10.3.3")
    }

    private goodCode() {
        file('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        file('src/test/java/org/gradle/TestClass1.java') << 'package org.gradle; public class TestClass1 { @org.junit.Test public void test1() { } }'
        file('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class2 { }'
        file('src/test/groovy/org/gradle/TestClass2.java') << 'package org.gradle; public class TestClass2 { @org.junit.Test public void test2() { } }'
    }

    private badCode() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/main/groovy/org/gradle/class2.java") << "package org.gradle; class class2 { }"
    }

    private badResources() {
        file("src/main/resources/bad.properties") << """hello=World"""
    }

    private sampleStylesheet() {
        normaliseFileSeparators(resources.getResource('/checkstyle-custom-stylesheet.xsl').getAbsolutePath())
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private void defaultLanguage(String defaultLanguage) {
        executer.withDefaultLocale(new Locale(defaultLanguage))
    }

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "groovy"
apply plugin: "checkstyle"

${mavenCentralRepository()}

dependencies {
    implementation localGroovy()
}

testing.suites.test.useJUnit()

checkstyle {
    toolVersion = '$version'
}
        """
    }

    private void writeConfigFileForResources() {
        file("config/checkstyle/checkstyle.xml").text = """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="NewlineAtEndOfFile"/>
</module>
        """
    }

    private void writeConfigFile() {
        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="SuppressionFilter">
        <property name="file" value="\${config_loc}/suppressions.xml"/>
    </module>
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
        """

        file("config/checkstyle/suppressions.xml") << """
<!DOCTYPE suppressions PUBLIC
    "-//Puppy Crawl//DTD Suppressions 1.1//EN"
    "http://www.puppycrawl.com/dtds/suppressions_1_1.dtd">

<suppressions>
    <suppress checks="TypeName"
          files="bad_name.java"/>
</suppressions>
        """
    }

    private void writeConfigFileWithWarnings() {
        file("config/checkstyle/checkstyle.xml").text = """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
<module name="Checker">
    <property name="severity" value="warning"/>
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
        """
    }
}
