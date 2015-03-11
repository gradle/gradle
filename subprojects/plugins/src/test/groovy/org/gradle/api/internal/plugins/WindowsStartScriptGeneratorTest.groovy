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

import org.gradle.api.scripting.ScriptGenerationDetails
import org.gradle.util.TextUtil
import org.gradle.util.WrapUtil
import spock.lang.Specification

class WindowsStartScriptGeneratorTest extends Specification {
    WindowsStartScriptGenerator generator = new WindowsStartScriptGenerator()
    JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails()

    def "uses expected template and line separator"() {
        expect:
        generator.defaultTemplateFilename == 'windowsStartScript.txt'
        generator.lineSeparator == TextUtil.windowsLineSeparator
    }

    def "classpath for windows script uses backslash as path separator and windows line separator"() {
        given:
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains("set CLASSPATH=%APP_HOME%\\path\\to\\Jar.jar")
    }

    def "windows script uses windows line separator"() {
        given:
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().split(TextUtil.windowsLineSeparator).length == 90
    }

    def "defaultJvmOpts is expanded properly in windows script"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=bar', '-Xint']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains('set DEFAULT_JVM_OPTS="-Dfoo=bar" "-Xint"')
    }

    def "defaultJvmOpts is expanded properly in windows script -- spaces"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=bar baz', '-Xint']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/set DEFAULT_JVM_OPTS="-Dfoo=bar baz" "-Xint"/)
    }

    def "defaultJvmOpts is expanded properly in windows script -- double quotes"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=b"ar baz', '-Xi""nt', '-Xpatho\\"logical']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/set DEFAULT_JVM_OPTS="-Dfoo=b\"ar baz" "-Xi\"\"nt" "-Xpatho\\\"logical"/)
    }

    def "defaultJvmOpts is expanded properly in windows script -- backslashes and shell metacharacters"() {
        given:
        details.defaultJvmOpts = ['-Dfoo=b\\ar baz', '-Xint%PATH%']
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/set DEFAULT_JVM_OPTS="-Dfoo=b\ar baz" "-Xint%%PATH%%"/)
    }

    def "determines application-relative path"() {
        given:
        details.scriptRelPath = "bin/sample/start"
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains('set APP_HOME=%DIRNAME%..\\..')
    }

    private JavaAppStartScriptGenerationDetails createScriptGenerationDetails() {
        ScriptGenerationDetails details = new JavaAppStartScriptGenerationDetails()
        details.applicationName = "TestApp"
        details.classpath = WrapUtil.toList("path/to/Jar.jar")
        details.scriptRelPath = "bin"
        details
    }
}
