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
package org.gradle.api.plugins.quality.findbugs

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Matchers
import org.gradle.util.Resources
import org.hamcrest.Matcher
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue

import static org.gradle.util.Matchers.containsLine
import static org.gradle.util.TextUtil.normaliseFileSeparators
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith
import static org.hamcrest.CoreMatchers.not

abstract class AbstractFindBugsPluginIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final Resources resources = new Resources()

    def setup() {
        writeBuildFile()
        executer.beforeExecute {
            expectDeprecationWarning()
        }
    }

    def "emits deprecating warning"() {
        when:
        succeeds("help")

        then:
        outputContains("The findbugs plugin has been deprecated.")
        outputContains("Consider using the com.github.spotbugs plugin instead.")
    }

    def "default findbugs version can be inspected"() {
        expect:
        succeeds("dependencies", "--configuration", "findbugs")
        output.contains "com.google.code.findbugs:findbugs:"
    }

    def "analyze good code"() {
        goodCode()
        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
    }

    void "analyze bad code"() {
        badCode()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':findbugsMain'.")
        failure.assertThatCause(startsWith("FindBugs rule violations were found. See the report at:"))
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.BadClass"))
    }

    void "can ignore failures"() {
        badCode()
        buildFile << """
            findbugs {
                ignoreFailures = true
            }
        """

        expect:
        succeeds("check")
        output.contains("FindBugs rule violations were found. See the report at:")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.BadClass"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.BadClassTest"))
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "is incremental"() {
        given:
        goodCode()

        expect:
        succeeds("findbugsMain") && ":findbugsMain" in nonSkippedTasks
        succeeds(":findbugsMain") && ":findbugsMain" in skippedTasks

        when:
        file("build/reports/findbugs/main.xml").delete()

        then:
        succeeds("findbugsMain") && ":findbugsMain" in nonSkippedTasks
    }

    def "cannot generate multiple reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled true
                html.enabled true
            }
        """

        and:
        goodCode()

        expect:
        fails "findbugsMain"

        failure.assertHasCause "FindBugs tasks can only have one report enabled"
    }

    def "can use optional arguments"() {
        given:
        buildFile << """
            findbugs {
                effort 'max'
                reportLevel 'high'
                includeFilterConfig resources.text.fromFile('include.xml')
                excludeFilter file('exclude.xml')
                visitors = ['FindDeadLocalStores', 'UnreadFields']
                omitVisitors = ['WaitInLoop', 'UnnecessaryMath']
            }
            findbugsMain.reports {
                xml.enabled true
            }
        """

        and:
        goodCode()
        badCode()

        and:
        writeFilterFile('include.xml', '.*')
        writeFilterFile('exclude.xml', 'org\\.gradle\\.Bad.*')

        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("Class1.java"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("Class1Test.java"))
    }

    void "excludes baseline bug for matching bug instance"() {
        given:
        String baselineExcludeFilename = 'baselineExclude.xml'

        buildFile << """
            findbugs {
                excludeBugsFilterConfig resources.text.fromFile('${baselineExcludeFilename}')
            }
            findbugsMain.reports {
                xml.enabled true
            }
        """

        and:
        badCode()

        when:
        writeBaselineBugsFilterFile(baselineExcludeFilename, new JavaClass('org.gradle', 'BadClass'))

        then:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.BadClass"))

        when:
        writeBaselineBugsFilterFile(baselineExcludeFilename, new JavaClass('org.gradle', 'SomeOtherClass'))

        then:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':findbugsMain'.")
        failure.assertThatCause(startsWith("FindBugs rule violations were found. See the report at:"))
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.BadClass"))
    }

    def "can generate html reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled false
                html.enabled true
            }
        """

        and:
        goodCode()

        when:
        run "findbugsMain"

        then:
        file("build/reports/findbugs/main.html").exists()
    }

    def "can generate html reports with a custom stylesheet"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled false
                html.enabled true
                html.stylesheet resources.text.fromFile('${anotherSampleStylesheet()}')
            }
        """
        goodCode()
        when:
        succeeds"findbugsMain"
        then:
        file("build/reports/findbugs/main.html").exists()
        file("build/reports/findbugs/main.html").assertContents(not(containsString("A custom Findbugs stylesheet")))

        when:
        // Change to a custom stylesheet
        buildFile << """
            findbugsMain.reports {
                html.stylesheet resources.text.fromFile('${sampleStylesheet()}')
            }
        """
        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.html").exists()
        file("build/reports/findbugs/main.html").assertContents(containsString("A custom Findbugs stylesheet"))
    }

    def "can generate xml with messages reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled true
                xml.withMessages true
                html.enabled false
            }
            findbugsMain.ignoreFailures true
        """

        and:
        badCode()

        when:
        run "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        containsXmlMessages(file("build/reports/findbugs/main.xml"))
    }

    def "can generate no reports"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled false
                html.enabled false
            }
        """

        and:
        goodCode()

        expect:
        succeeds "findbugsMain"

        and:
        !file("build/reports/findbugs/main.html").exists()
        !file("build/reports/findbugs/main.xml").exists()
    }

    def "can analyze a lot of classes"() {
        goodCode(800)
        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class800"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class1Test"))
        file("build/reports/findbugs/test.xml").assertContents(containsClass("org.gradle.Class800Test"))
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "is incremental for reporting settings"() {
        given:
        buildFile << """
            findbugsMain.reports {
                xml.enabled true
            }
        """

        and:
        goodCode()

        when:
        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        ":findbugsMain" in nonSkippedTasks
        !(":findbugsMain" in skippedTasks)

        when:
        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        !(":findbugsMain" in nonSkippedTasks)
        ":findbugsMain" in skippedTasks

        when:
        buildFile << """
            findbugsMain.reports {
                xml.enabled false
            }
        """

        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        ":findbugsMain" in nonSkippedTasks
        !(":findbugsMain" in skippedTasks)
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "is incremental for withMessage"() {
        given:
        buildFile << """
            findbugsMain {
                reports {
                    xml.enabled true
                    xml.withMessages true
                }

                ignoreFailures true
            }
        """

        and:
        badCode()

        when:
        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        containsXmlMessages(file("build/reports/findbugs/main.xml"))
        ":findbugsMain" in nonSkippedTasks
        !(":findbugsMain" in skippedTasks)

        when:
        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        containsXmlMessages(file("build/reports/findbugs/main.xml"))
        !(":findbugsMain" in nonSkippedTasks)
        ":findbugsMain" in skippedTasks

        when:
        buildFile << """
            findbugsMain {
                reports {
                    xml.enabled true
                    xml.withMessages false
                }

                ignoreFailures true
            }
        """

        succeeds "findbugsMain"

        then:
        file("build/reports/findbugs/main.xml").exists()
        !containsXmlMessages(file("build/reports/findbugs/main.xml"))
        ":findbugsMain" in nonSkippedTasks
        !(":findbugsMain" in skippedTasks)
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "is withMessage ignored for non-XML report setting"() {
        given:
        buildFile << """
            findbugsMain {
                reports {
                    xml.enabled false
                    xml.withMessages true
                    html.enabled true
                }
            }
        """

        and:
        goodCode()

        when:
        succeeds "findbugsMain"

        then:
        !file("build/reports/findbugs/main.xml").exists()
        file("build/reports/findbugs/main.html").exists()

        when:
        buildFile << """
            findbugsMain.reports {
                xml.withMessages false
            }
        """

        and:
        succeeds "findbugsMain"

        then:
        !file("build/reports/findbugs/main.xml").exists()
        file("build/reports/findbugs/main.html").exists()
        !(":findbugsMain" in nonSkippedTasks)
        ":findbugsMain" in skippedTasks
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3214")
    def "Thrown java.lang.Error due to missing transitive dependency is handled and fails the build"() {
        given:
        buildFile << 'configurations.findbugs.transitive = false'

        and:
        goodCode()

        expect:
        fails("check")
        failure.assertHasDescription "Execution failed for task ':findbugsMain'."
        failure.assertHasCause 'Failed to run Gradle FindBugs Worker'
        failure.assertThatCause(Matchers.matchesRegexp("org[\\./]apache[\\./]bcel[\\./]classfile[\\./]ClassFormatException"))
    }

    def "valid adjustPriority extra args"() {
        given:
        file("src/main/java/org/gradle/ClassUsingCaseConversion.java") <<
            'package org.gradle; public class ClassUsingCaseConversion { public boolean useConversion() { return "Hi".toUpperCase().equals("HI"); } }'

        expect:
        succeeds("findbugsMain")

        when:
        // Test extraArgs using DM_CONVERT_CASE which FindBugs treats as a LOW confidence warning.  We will use
        // extraArgs to boost the confidence which should make it be reported
        buildFile << """
            findbugsMain {
                extraArgs '-adjustPriority', 'DM_CONVERT_CASE=raise,DM_CONVERT_CASE=raise'
            }
        """

        then:
        fails("findbugsMain")
        failure.assertHasDescription("Execution failed for task ':findbugsMain'.")
        failure.assertThatCause(startsWith("FindBugs rule violations were found. See the report at:"))
        file("build/reports/findbugs/main.xml").exists()
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.ClassUsingCaseConversion"))
        file("build/reports/findbugs/main.xml").assertContents(containsString("DM_CONVERT_CASE"))
    }

    def "fails when given invalid extraArgs"() {
        given:
        goodCode()
        and:
        buildFile << """
            findbugsMain {
                extraArgs 'gobbledygook'
            }
        """

        expect:
        fails "check"
        failure.assertHasCause 'FindBugs encountered an error.'
        failure.assertHasDescription "Execution failed for task ':findbugsMain'."
    }

    def "valid jvm args"() {
        given:
        badCode()
        buildFile << extensionProgressConfiguration(true)
        // We don't have many jvm args to use for FindBugs, see:
        // http://findbugs.sourceforge.net/manual/running.html
        // https://github.com/findbugsproject/findbugs/blob/master/findbugs/build.xml#L771
        buildFile << '''
            findbugs {
                jvmArgs = ['-Duser.language=fr']
            }
            findbugsMain.reports {
                html.enabled true
                xml.enabled false
            }
        '''

        fails('findbugsMain')

        expect:
        file('build/reports/findbugs/main.html').text.contains('DM_EXIT: La méthode invoque System.exit(...)') // <- this is french
    }

    def "fails when given invalid jvmArgs"() {
        given:
        goodCode()

        and:
        buildFile << """
            findbugsMain {
                jvmArgs '-XInvalid'
            }
        """
        // java.io.IOException: The pipe is being closed on windows
        executer.withStackTraceChecksDisabled()

        expect:
        fails("check")
        failure.assertHasDescription("Execution failed for task ':findbugsMain'.")
        failure.assertHasCause("Failed to run Gradle FindBugs Worker")
        failure.assertThatCause(Matchers.matchesRegexp("Process 'Gradle FindBugs Worker [0-9]+' finished with non-zero exit value 1"))
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    def "out-of-date with mixed Java and Groovy sources"() {
        given:
        goodCode()
        buildFile << """
            apply plugin: 'groovy'
            dependencies {
                compile localGroovy()
            }
        """
        file("src/main/groovy/org/gradle/Groovy1.groovy") << """
            package org.gradle
            
            class Groovy1 {
                boolean is() { true }
            }
        """
        expect:
        succeeds("check")
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Groovy1"))
        file("build/reports/findbugs/main.xml").assertContents(containsClass("org.gradle.Class1"))

        when:
        file("src/main/java/org/gradle/Class1.java").text = """
            package org.gradle; 
            public class Class1 { 
                public boolean isFoo(Object arg) { return true; } 
                public boolean isNotFoo(Object arg) { return false; } 
            }
        """
        then:
        succeeds("check")
        result.assertTaskNotSkipped(":findbugsMain")

        when:
        file("src/main/groovy/org/gradle/Groovy1.groovy").text = """
            package org.gradle
            
            class Groovy1 {
                boolean is() { true }
                boolean isNot() { false }
            }
        """
        then:
        succeeds("check")
        result.assertTaskNotSkipped(":findbugsMain")
    }

    def "does not fail if resources are generated into classes"() {
        given:
        goodCode()
        buildFile << """
            compileJava {
                doLast {
                    def manifest = new File(destinationDir, "META-INF/MANIFEST.MF")
                    manifest.parentFile.mkdirs()
                    manifest.text = "manifest"
                    def properties = new File(destinationDir, "com/example/service.properties")
                    properties.parentFile.mkdirs()
                    properties.text = "someProp=value"
                }
            } 
        """
        when:
        succeeds("check")
        then:
        result.assertNotOutput("Wrong magic bytes")
    }

    @Issue("https://github.com/gradle/gradle/issues/1307")
    def "does not render progress output by default"() {
        given:
        goodCode()

        when:
        run "findbugsMain"

        then:
        !output.contains("Scanning archives")
        !output.contains("Done with analysis")
    }

    @Issue("https://github.com/gradle/gradle/issues/1307")
    def "can disable progress output"() {
        given:
        buildFile << extensionProgressConfiguration(false)

        and:
        goodCode()

        when:
        run "findbugsMain"

        then:
        !output.contains("Scanning archives")
        !output.contains("Done with analysis")
    }

    @Issue("https://github.com/gradle/gradle/issues/1307")
    def "can enable progress output"() {
        given:
        buildFile << extensionProgressConfiguration(true)

        and:
        goodCode()

        when:
        run "findbugsMain"

        then:
        output.contains("Scanning archives")
        output.contains("Done with analysis")
    }

    @Issue("https://github.com/gradle/gradle/issues/2326")
    def "check task should not be up-to-date after clean if it only outputs to console"() {
        given:
        badCode()
        buildFile << """
            findbugs {
                ignoreFailures true
                showProgress true
            }
            tasks.withType(FindBugs) {
                reports {
                    html.enabled false
                    xml.enabled false
                    text.enabled false
                }
            }
        """

        when:
        succeeds('check')
        succeeds('clean', 'check')

        then:
        nonSkippedTasks.contains(':findbugsMain')
        output.contains("Analyzing classes")
    }

    private static boolean containsXmlMessages(File xmlReportFile) {
        new XmlSlurper().parseText(xmlReportFile.text).BugInstance.children().collect { it.name() }.containsAll(['ShortMessage', 'LongMessage'])
    }

    private goodCode(int numberOfClasses = 1) {
        1.upto(numberOfClasses) {
            file("src/main/java/org/gradle/Class${it}.java") << "package org.gradle; public class Class${it} { public boolean isFoo(Object arg) { return true; } }"
            file("src/test/java/org/gradle/Class${it}Test.java") << "package org.gradle; public class Class${it}Test { public boolean isFoo(Object arg) { return true; } }"
        }
    }

    private badCode() {
        // Has DM_EXIT
        file('src/main/java/org/gradle/BadClass.java') << 'package org.gradle; public class BadClass { public boolean isFoo(Object arg) { System.exit(1); return true; } }'
        // Has ES_COMPARING_PARAMETER_STRING_WITH_EQ
        file('src/test/java/org/gradle/BadClassTest.java') << 'package org.gradle; public class BadClassTest { public boolean isFoo(Object arg) { return "true" == "false"; } }'
    }

    private sampleStylesheet() {
        normaliseFileSeparators(resources.getResource('/findbugs-custom-stylesheet.xsl').absolutePath)
    }

    private anotherSampleStylesheet() {
        normaliseFileSeparators(resources.getResource('/findbugs-another-custom-stylesheet.xsl').absolutePath)
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className))
    }

    private void writeBuildFile() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "findbugs"

            ${mavenCentralRepository()}
        """
    }

    private void writeFilterFile(String filename, String className) {
        file(filename) << """
            <FindBugsFilter>
            <Match>
                <Class name="${className}" />
            </Match>
            </FindBugsFilter>
        """
    }

    private void writeBaselineBugsFilterFile(String filename, JavaClass javaClass) {
        file(filename) << """
            <BugCollection>
                <BugInstance type="DM_EXIT" priority="2" rank="16" abbrev="Dm" category="BAD_PRACTICE">
                    <Class classname="${javaClass.fullyQualifiedClassName}">
                        <SourceLine classname="${javaClass.fullyQualifiedClassName}" start="1" end="1" sourcefile="${javaClass.classFilename}" sourcepath="${javaClass.fullyQualifiedClassFilename}"/>
                    </Class>
                    <Method classname="${javaClass.fullyQualifiedClassName}" name="isFoo" signature="(Ljava/lang/Object;)Z" isStatic="false">
                        <SourceLine classname="${javaClass.fullyQualifiedClassName}" start="1" end="1" startBytecode="0" endBytecode="57" sourcefile="${javaClass.classFilename}" sourcepath="${javaClass.fullyQualifiedClassFilename}"/>
                    </Method>
                    <SourceLine classname="${javaClass.fullyQualifiedClassName}" start="1" end="1" startBytecode="1" endBytecode="1" sourcefile="${javaClass.classFilename}" sourcepath="${javaClass.fullyQualifiedClassFilename}"/>
                </BugInstance>
            </BugCollection>
        """
    }

    private class JavaClass {
        String pkg
        String className
        String fullyQualifiedClassName
        String classFilename
        String fullyQualifiedClassFilename

        JavaClass(String pkg, String className) {
            this.pkg = pkg
            this.className = className
            fullyQualifiedClassName = "${pkg}.${className}"
            classFilename = "${className}.java"
            fullyQualifiedClassFilename = "${pkg.replaceAll('\\.', '/')}/${classFilename}"
        }
    }

    static String extensionProgressConfiguration(boolean flag) {
        """
            findbugs {
                showProgress = $flag 
            }
        """
    }
}
