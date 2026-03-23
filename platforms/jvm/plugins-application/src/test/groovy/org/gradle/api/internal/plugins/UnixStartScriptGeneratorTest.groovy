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
import org.gradle.util.internal.TextUtil
import spock.lang.Issue
import spock.lang.Specification

import java.util.regex.Pattern

class UnixStartScriptGeneratorTest extends Specification {

    UnixStartScriptGenerator generator = new UnixStartScriptGenerator()

    def "classpath for unix script uses slashes as path separator"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(null, 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains("CLASSPATH=\$APP_HOME/path/to/Jar.jar")
    }

    def "unix script uses unix line separator"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(null, 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        !destination.toString().contains(TextUtil.windowsLineSeparator)
        destination.toString().contains(TextUtil.unixLineSeparator)
    }

    def "defaultJvmOpts is expanded properly in unix script"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(['-Dfoo=bar', '-Xint'], 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains('DEFAULT_JVM_OPTS=\'"-Dfoo=bar" "-Xint"\'')
    }

    def "defaultJvmOpts is expanded properly in unix script -- spaces"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(['-Dfoo=bar baz', '-Xint'], 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=bar baz" "-Xint"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- double quotes"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(['-Dfoo=b"ar baz', '-Xi""nt'], 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\"ar baz" "-Xi\"\"nt"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- single quotes"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(['-Dfoo=b\'ar baz', '-Xi\'\'n`t'], 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=b'"'"'ar baz" "-Xi'"'"''"'"'n'"`"'t"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- backslashes and shell metacharacters"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(['-Dfoo=b\\ar baz', '-Xint$PATH'], 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\\ar baz" "-Xint/ + '\\$PATH' + /"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- empty list"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails([], 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(/DEFAULT_JVM_OPTS=""/)
    }

    def "generates correct output for #type entry point"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(null, 'bin/sample/start', entryPoint)
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        // This is a little difficult to locate exactly, here I used the trailing part of the `set --` command.
        destination.toString().find(/${Pattern.quote(entryPointArgs)} \\[\n\s]+"\$@"/)

        where:
        type                          | entryPoint                                        | entryPointArgs
        "main class"                  | new MainClass("com.example.Main")                 | 'com.example.Main'
        "executable jar"              | new ExecutableJar("example.jar")                  | '-jar "$APP_HOME/example.jar"'
        "main module"                 | new MainModule("com.example", null)               | '--module com.example'
        "main module with main class" | new MainModule("com.example", "com.example.Main") | '--module com.example/com.example.Main'
    }

    @Issue("https://github.com/gradle/gradle/issues/33415")
    def "Do not set classpath if it is empty"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(null, 'bin', new MainClass(""), classpath)
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains(text) == result

        where:
        classpath             | text                     | result
        []                    | '-classpath "$CLASSPATH' | false
        []                    | 'CLASSPATH='             | false
        ['path\\to\\Jar.jar'] | '-classpath "$CLASSPATH' | true
        ['path\\to\\Jar.jar'] | 'CLASSPATH='             | true
    }

    @Issue("https://github.com/gradle/gradle/issues/30101")
    def "uses github permalinks in embedded documentation when gitRef specified"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(
            null, 'bin', new MainClass(""), ['path\\to\\Jar.jar'], "6c9eca778c871a6310d2c3f2c3d3f8e67a915538")
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        !destination.toString().contains("https://github.com/gradle/gradle/blob/HEAD/")
        destination.toString().contains("https://github.com/gradle/gradle/blob/6c9eca778c871a6310d2c3f2c3d3f8e67a915538/")
    }

    @Issue("https://github.com/gradle/gradle/issues/30101")
    def "uses HEAD in github links embedded documentation when gitRef not specified"() {
        given:
        JavaAppStartScriptGenerationDetails details = createScriptGenerationDetails(null, 'bin')
        Writer destination = new StringWriter()

        when:
        generator.generateScript(details, destination)

        then:
        destination.toString().contains("https://github.com/gradle/gradle/blob/HEAD/")
    }

    private JavaAppStartScriptGenerationDetails createScriptGenerationDetails(
        List<String> defaultJvmOpts,
        String scriptRelPath,
        AppEntryPoint appEntryPoint = new MainClass(""),
        List<String> classpath = ['path\\to\\Jar.jar'],
        String gitRef = "HEAD" // HEAD convention in CreateStartScripts / WrapperGenerator always populates a specific ref
    ) {
        final String applicationName = 'TestApp'
        return new DefaultJavaAppStartScriptGenerationDetails(applicationName, gitRef, null, null, appEntryPoint, defaultJvmOpts, classpath, [], scriptRelPath, null)
    }
}
