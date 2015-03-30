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

package org.gradle.api.internal.plugins

import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails
import org.gradle.jvm.application.scripts.ScriptGenerationDetails
import org.gradle.util.TextUtil
import org.gradle.util.WrapUtil
import spock.lang.Specification

class UnixStartScriptGeneratorTest extends Specification {
    UnixStartScriptGenerator generator = new UnixStartScriptGenerator()
    JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails()

    def "uses expected template and line separator"() {
        expect:
        generator.defaultTemplateFilename == 'unixStartScript.txt'
        generator.lineSeparator == TextUtil.unixLineSeparator
    }

    def "classpath for unix script uses slashes as path separator"() {
        given:
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains("CLASSPATH=\$APP_HOME/path/to/Jar.jar")
    }

    def "unix script uses unix line separator"() {
        given:
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().split(TextUtil.unixLineSeparator).length == 164
    }

    def "defaultJvmOpts is expanded properly in unix script"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=bar', '-Xint']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains('DEFAULT_JVM_OPTS=\'"-Dfoo=bar" "-Xint"\'')
    }

    def "defaultJvmOpts is expanded properly in unix script -- spaces"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=bar baz', '-Xint']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=bar baz" "-Xint"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- double quotes"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=b"ar baz', '-Xi""nt']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\"ar baz" "-Xi\"\"nt"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- single quotes"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=b\'ar baz', '-Xi\'\'n`t']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=b'"'"'ar baz" "-Xi'"'"''"'"'n'"`"'t"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- backslashes and shell metacharacters"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=b\\ar baz', '-Xint$PATH']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\\ar baz" "-Xint/ + '\\$PATH' + /"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- empty list"() {
        given:
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS=""/)
    }

    def "determines application-relative path"() {
        given:
        details.scriptRelPath = "bin/sample/start"
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains('cd "`dirname \\"$PRG\\"`/../.." >&-')
    }

    private JavaAppStartScriptGenerationDetails createScriptGenerationDetails() {
        ScriptGenerationDetails details = new JavaAppStartScriptGenerationDetails()
        details.applicationName = "TestApp"
        details.classpath = WrapUtil.toList("path\\to\\Jar.jar")
        details.scriptRelPath = "bin"
        details
    }
}
