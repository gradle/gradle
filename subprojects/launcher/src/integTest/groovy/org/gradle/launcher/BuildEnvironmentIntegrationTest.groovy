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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.charset.Charset

@DoesNotSupportNonAsciiPaths(reason = "Some tests need to run with ASCII encoding")
class BuildEnvironmentIntegrationTest extends AbstractIntegrationSpec {

    @Unroll("default locale for gradle build switched to #locale")
    def "builds can be executed with different default locales"() {
        given:
        executer.withDefaultLocale(locale)

        and:
        buildFile.setText("""
task check {
    doLast {
        assert Locale.getDefault().toString() == "${locale}"
    }
}
""", "UTF-8")

        expect:
        succeeds 'check'

        where:
        locale << [nonDefaultLocale, Locale.default]
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3145")
    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "locale props given on the command line are respected"() {
        given:
        def nonDefaultLocale = getNonDefaultLocale()
        executer.withArguments("-Duser.language=$nonDefaultLocale.language", "-Duser.country=$nonDefaultLocale.country")

        and:
        buildFile.setText("""
task check {
    doLast {
        assert Locale.getDefault().toString() == "${nonDefaultLocale}"
    }
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "locale props given in gradle.properties are respected"() {
        given:
        def nonDefaultLocale = getNonDefaultLocale()
        file("gradle.properties") << "org.gradle.jvmargs=-Duser.language=$nonDefaultLocale.language -Duser.country=$nonDefaultLocale.country"

        and:
        buildFile.setText("""
task check {
    doLast {
        assert Locale.getDefault().toString() == "${nonDefaultLocale}"
    }
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "default file encoding set in gradle.properties is respected"() {
        given:
        def nonDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) }.find { it != Charset.defaultCharset() }

        file("gradle.properties") << "org.gradle.jvmargs=-Dfile.encoding=${nonDefaultEncoding.name()}"

        and:
        buildFile.setText("""
task check {
    doLast {
        assert ${Charset.class.name}.defaultCharset().name() == "${nonDefaultEncoding}"
    }
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3145")
    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "default file encoding set on command line is respected"() {
        given:
        def nonDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) }.find { it != Charset.defaultCharset() }

        executer.withArgument("-Dfile.encoding=${nonDefaultEncoding.name()}")

        and:
        buildFile.setText("""
task check {
    doLast {
        assert ${Charset.class.name}.defaultCharset().name() == "${nonDefaultEncoding}"
    }
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    Locale getTurkishLocale() {
        new Locale("tr", "TR")
    }

    Locale getNonDefaultLocale() {
        [new Locale('de'), new Locale('en')].find { it != Locale.default }
    }

    def executerEncoding(String inputEncoding) {
        if (inputEncoding) {
            executer.withDefaultCharacterEncoding(inputEncoding)
        }
    }

    @Unroll("build default encoding matches specified - input = #inputEncoding, expectedEncoding: #expectedEncoding")
    def "build default encoding matches specified"(String inputEncoding, String expectedEncoding) {
        given:
        executerEncoding inputEncoding

        and:
        buildFile.write """
            task echoDefaultEncoding {
                doFirst {
                    println "default encoding: " + java.nio.charset.Charset.defaultCharset().name()
                }
            }
        """, expectedEncoding

        when:
        succeeds "echoDefaultEncoding"

        then:
        output.contains "default encoding: $expectedEncoding"

        where:
        inputEncoding | expectedEncoding
        "UTF-8"       | "UTF-8"
        "US-ASCII"    | "US-ASCII"
        null          | Charset.defaultCharset().name()
    }

    @Unroll("forked java processes inherit default encoding - input = #inputEncoding, expectedEncoding: #expectedEncoding")
    def "forked java processes inherit default encoding"() {
        given:
        executerEncoding inputEncoding

        and:
        file("src", "main", "java").mkdirs()
        file("src", "main", "java", "EchoEncoding.java").write """
            package echo;

            import java.nio.charset.Charset;

            public class EchoEncoding {
                public static void main(String[] args) {
                    System.out.println("default encoding: " + Charset.defaultCharset().name());
                }
            }
        """, executer.getDefaultCharacterEncoding()

        and:
        buildFile.write """
            apply plugin: "java"

            task echoDefaultEncoding(type: JavaExec) {
                classpath = project.layout.files(compileJava)
                mainClass = "echo.EchoEncoding"
            }
        """, expectedEncoding

        when:
        succeeds "echoDefaultEncoding"

        then:
        output.contains "default encoding: $expectedEncoding"

        where:
        inputEncoding | expectedEncoding
        "UTF-8"       | "UTF-8"
        "US-ASCII"    | "US-ASCII"
        null          | Charset.defaultCharset().name()
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        executer.useOnlyRequestedJvmOpts()
        return super.succeeds(tasks)
    }

    @Issue("GRADLE-3470")
    def "command-line options are not affected by locale"() {
        given:
        executer.withCommandLineGradleOpts("-Duser.language=${turkishLocale.language}", "-Duser.country=${turkishLocale.country}")
        expect:
        succeeds 'help', '--console=PLAIN'
    }

    @Issue("https://github.com/gradle/gradle/issues/1001")
    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "system properties from gradle.properties are available to init scripts for buildSrc"() {
        given:
        executer.requireOwnGradleUserHomeDir()
        executer.gradleUserHomeDir.file("init.gradle") << """
            println 'running init script'
            assert System.getProperty('foo') == 'bar'
        """
        executer.gradleUserHomeDir.file("gradle.properties") << "systemProp.foo=bar"
        // Add something to buildSrc to have it evaluated
        file("buildSrc/settings.gradle") << """
            assert System.getProperty('foo') == 'bar'
            rootProject.name = 'myBuildSrc'
        """

        expect:
        succeeds 'help'
    }
}
