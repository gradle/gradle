/*
 * Copyright 2015 the original author or authors.
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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.quality.integtest.fixtures.CheckstyleCoverage
import org.gradle.util.Resources
import org.hamcrest.Matcher
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.TextUtil.normaliseFileSeparators
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith

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
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class2"))
        file("build/reports/checkstyle/test.xml").assertContents(containsClass("org.gradle.TestClass1"))
        file("build/reports/checkstyle/test.xml").assertContents(containsClass("org.gradle.TestClass2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.Class2"))
        file("build/reports/checkstyle/test.html").assertContents(containsClass("org.gradle.TestClass1"))
        file("build/reports/checkstyle/test.html").assertContents(containsClass("org.gradle.TestClass2"))
    }

    @NotYetImplemented
    @Issue("GRADLE-3432")
    def "analyze bad resources"() {
        defaultLanguage('en')
        writeConfigFileForResources()
        badResources()

        expect:
        fails('check')

        file("build/reports/checkstyle/main.xml").assertContents(containsLine(containsString("bad.properties")))
        file("build/reports/checkstyle/main.html").assertContents(containsLine(containsString("bad.properties")))
    }

    def "analyze bad code"() {
        defaultLanguage('en')
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertThatCause(startsWith("Checkstyle rule violations were found. See the report at:"))
        failure.error.contains("Name 'class1' must match pattern")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    def "can suppress console output"() {
        given:
        defaultLanguage('en')
        badCode()

        when:
        buildFile << "checkstyle { showViolations = false }"

        then:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertThatCause(startsWith("Checkstyle rule violations were found. See the report at:"))
        !failure.error.contains("Name 'class1' must match pattern")
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
        output.contains("Checkstyle rule violations were found. See the report at:")
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
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "is incremental"() {
        given:
        goodCode()

        expect:
        succeeds("checkstyleMain") && ":checkstyleMain" in nonSkippedTasks
        executer.withArgument("-i")
        succeeds("checkstyleMain") && ":checkstyleMain" in skippedTasks

        when:
        file("build/reports/checkstyle/main.xml").delete()
        file("build/reports/checkstyle/main.html").delete()

        then:
        succeeds("checkstyleMain") && ":checkstyleMain" in nonSkippedTasks
    }

    def "can configure reporting"() {
        given:
        goodCode()

        when:
        buildFile << """
            checkstyleMain.reports {
                xml.destination file("foo.xml")
                html.destination file("bar.html")
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
                html.enabled true
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
                    xml.enabled false
                    html.enabled true
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

    private goodCode() {
        file('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        file('src/test/java/org/gradle/TestClass1.java') << 'package org.gradle; class TestClass1 { }'
        file('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class2 { }'
        file('src/test/groovy/org/gradle/TestClass2.java') << 'package org.gradle; class TestClass2 { }'
    }

    private badCode() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/test/java/org/gradle/testclass1.java") << "package org.gradle; class testclass1 { }"
        file("src/main/groovy/org/gradle/class2.java") << "package org.gradle; class class2 { }"
        file("src/test/groovy/org/gradle/testclass2.java") << "package org.gradle; class testclass2 { }"
    }

    private badResources() {
        file("src/main/resources/bad.properties") << """hello=World"""
    }

    private sampleStylesheet() {
        normaliseFileSeparators(resources.getResource('/checkstyle-custom-stylesheet.xsl').getAbsolutePath())
    }

    private Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

    private void defaultLanguage(String defaultLanguage) {
        executer.withDefaultLocale(new Locale(defaultLanguage))
    }

    private void writeBuildFile() {
        file("build.gradle") << """
apply plugin: "groovy"
apply plugin: "checkstyle"

repositories {
    mavenCentral()
}

dependencies {
    compile localGroovy()
}

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
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
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
