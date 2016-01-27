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
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.charset.Charset

class BuildEnvironmentIntegrationTest extends AbstractIntegrationSpec {

    @Unroll("default locale for gradle build switched to #locale")
    def "builds can be executed with different default locales"() {
        given:
        executer.withDefaultLocale(locale)

        and:
        buildFile.setText("""
task check << {
    assert Locale.getDefault().toString() == "${locale}"
}
""", "UTF-8")

        expect:
        succeeds 'check'

        where:
        locale << [nonDefaultLocale, Locale.default]
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3145")
    def "locale props given on the command line are respected"() {
        given:
        def nonDefaultLocale = getNonDefaultLocale()
        executer.requireGradleHome()
        executer.withArguments("-Duser.language=$nonDefaultLocale.language", "-Duser.country=$nonDefaultLocale.country")

        and:
        buildFile.setText("""
task check << {
    assert Locale.getDefault().toString() == "${nonDefaultLocale}"
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    def "locale props given in gradle.properties are respected"() {
        given:
        def nonDefaultLocale = getNonDefaultLocale()
        executer.requireGradleHome()
        file("gradle.properties") << "org.gradle.jvmargs=-Duser.language=$nonDefaultLocale.language -Duser.country=$nonDefaultLocale.country"

        and:
        buildFile.setText("""
task check << {
    assert Locale.getDefault().toString() == "${nonDefaultLocale}"
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    def "default file encoding set in gradle.properties is respected"() {
        given:
        def nonDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) }.find { it != Charset.defaultCharset() }

        executer.requireGradleHome()
        file("gradle.properties") << "org.gradle.jvmargs=-Dfile.encoding=${nonDefaultEncoding.name()}"

        and:
        buildFile.setText("""
task check << {
    assert ${Charset.class.name}.defaultCharset().name() == "${nonDefaultEncoding}"
}
""", "UTF-8")

        expect:
        succeeds 'check'
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3145")
    def "default file encoding set on command line is respected"() {
        given:
        def nonDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) }.find { it != Charset.defaultCharset() }

        executer.requireGradleHome()
        executer.withArgument("-Dfile.encoding=${nonDefaultEncoding.name()}")

        and:
        buildFile.setText("""
task check << {
    assert ${Charset.class.name}.defaultCharset().name() == "${nonDefaultEncoding}"
}
""", "UTF-8")

        expect:
        succeeds 'check'
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
                classpath = project.files(compileJava)
                main "echo.EchoEncoding"
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
        executer.useDefaultBuildJvmArgs()
        return super.succeeds(tasks)
    }
}
