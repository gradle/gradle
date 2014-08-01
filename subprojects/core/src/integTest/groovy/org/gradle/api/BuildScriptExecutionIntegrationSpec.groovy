/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.charset.Charset

class BuildScriptExecutionIntegrationSpec extends AbstractIntegrationSpec {

    def "build scripts must be encoded using utf-8"() {
        given:
        executer.withDefaultCharacterEncoding("ISO-8859-15")

        and:
        buildFile.setText("""
task check << {
    assert java.nio.charset.Charset.defaultCharset().name() == "ISO-8859-15"
    // embed a euro character in the text - this is encoded differently in ISO-8859-12 and UTF-8
    assert '\u20AC'.charAt(0) == 0x20AC
}
""", "UTF-8")
        assert file('build.gradle').getText("ISO-8859-15") != file('build.gradle').getText("UTF-8")
        expect:
        succeeds 'check'
    }

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

    @Issue("http://issues.gradle.org/browse/GRADLE-3145")
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

    @Issue("http://issues.gradle.org/browse/GRADLE-3145")
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

}
