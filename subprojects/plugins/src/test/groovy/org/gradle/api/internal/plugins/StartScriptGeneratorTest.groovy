/*
 * Copyright 2009 the original author or authors.
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

import spock.lang.Specification
import org.gradle.util.WrapUtil
import org.gradle.util.TextUtil

class StartScriptGeneratorTest extends Specification {

    def generator = new StartScriptGenerator();

    def "classpath for unix script uses slashes as path separator"() {
        given:
        generator.applicationName = "TestApp"
        generator.setClasspath(WrapUtil.toList("path\\to\\Jar.jar"))
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains("CLASSPATH=\$APP_HOME/path/to/Jar.jar")
    }

    def "classpath eliminates duplicate components, retaining only the first mention"() {
        given:
        generator.applicationName = "TestApp"
        generator.setClasspath(WrapUtil.toList("lib\\foo.jar", "lib\\classes", "lib\\classes-dup", "lib\\dup.jar", "lib\\bar.jar", "lib\\dup.jar", "lib\\classes-dup", 'lib\\baz.jar'))
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        -1 < Collections.indexOfSubList(unixScriptContent.split(TextUtil.unixLineSeparator)*.trim(),
                ['CLASSPATH=$APP_HOME/lib/foo.jar',
                 'CLASSPATH=$CLASSPATH:$APP_HOME/lib/classes',
                 'CLASSPATH=$CLASSPATH:$APP_HOME/lib/classes-dup',
                 'CLASSPATH=$CLASSPATH:$APP_HOME/lib/dup.jar',
                 'CLASSPATH=$CLASSPATH:$APP_HOME/lib/bar.jar',
                 'CLASSPATH=$CLASSPATH:$APP_HOME/lib/baz.jar'
                ]
        )
        -1 < Collections.indexOfSubList(windowsScriptContent.split(TextUtil.windowsLineSeparator)*.trim(),
                ['set CLASSPATH=%APP_HOME%\\lib\\foo.jar',
                 'set CLASSPATH=%CLASSPATH%;%APP_HOME%\\lib\\classes',
                 'set CLASSPATH=%CLASSPATH%;%APP_HOME%\\lib\\classes-dup',
                 'set CLASSPATH=%CLASSPATH%;%APP_HOME%\\lib\\dup.jar',
                 'set CLASSPATH=%CLASSPATH%;%APP_HOME%\\lib\\bar.jar',
                 'set CLASSPATH=%CLASSPATH%;%APP_HOME%\\lib\\baz.jar'
                ]
        )
    }

    def "when classpathStyle=EXPLICIT, the command-line contains the classpath param"() {
        given:
        generator.applicationName = "TestApp"
        generator.scriptRelPath = "bin"
        generator.classpathStyle = ClasspathStyle.EXPLICIT
        when:
        def unixJavaCommands = generator.generateUnixScriptContent().split(TextUtil.unixLineSeparator)*.trim().grep(~'exec "\\$JAVACMD" .*')
        def windowsJavaCommands = generator.generateWindowsScriptContent().split(TextUtil.windowsLineSeparator)*.trim().grep(~'"%JAVA_EXE%" .*')
        then:
        unixJavaCommands.size()==1
        windowsJavaCommands.size()==1
        unixJavaCommands[0] =~ ~/\s(?:-classpath|-cp)\s/
        windowsJavaCommands[0] =~ ~/\s(?:-classpath|-cp)\s/
    }

    def "when classpathStyle=ENVIRONMENT, the command-line does not contain the classpath param"() {
        given:
        generator.applicationName = "TestApp"
        generator.scriptRelPath = "bin"
        generator.classpathStyle = ClasspathStyle.ENVIRONMENT
        when:
        def unixJavaCommands = generator.generateUnixScriptContent().split(TextUtil.unixLineSeparator)*.trim().grep(~'exec "\\$JAVACMD" .*')
        def windowsJavaCommands = generator.generateWindowsScriptContent().split(TextUtil.windowsLineSeparator)*.trim().grep(~'"%JAVA_EXE%" .*')
        then:
        unixJavaCommands.size()==1
        windowsJavaCommands.size()==1
        !(unixJavaCommands[0] =~ ~/\s(?:-classpath|-cp)\s/)
        !(windowsJavaCommands[0] =~ ~/\s(?:-classpath|-cp)\s/)
    }

    def "unix script uses unix line separator"() {
        given:
        generator.applicationName = "TestApp"
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.split(TextUtil.windowsLineSeparator).length == 1
        unixScriptContent.split(TextUtil.unixLineSeparator).length == 165
    }

    def "classpath for windows script uses backslash as path separator and windows line separator"() {
        given:
        generator.applicationName = "TestApp"
        generator.setClasspath(WrapUtil.toList("path/to/Jar.jar"))
        generator.scriptRelPath = "bin"
        when:
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        windowsScriptContent.contains("set CLASSPATH=%APP_HOME%\\path\\to\\Jar.jar")
        windowsScriptContent.split(TextUtil.windowsLineSeparator).length == 91
    }

    def "windows script uses windows line separator"() {
        given:
        generator.applicationName = "TestApp"
        generator.scriptRelPath = "bin"
        when:
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        windowsScriptContent.split(TextUtil.windowsLineSeparator).length == 91
    }

    def "defaultJvmOpts is expanded properly in windows script"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=bar', '-Xint']
        generator.scriptRelPath = "bin"
        when:
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        windowsScriptContent.contains('set DEFAULT_JVM_OPTS="-Dfoo=bar" "-Xint"')
    }

    def "defaultJvmOpts is expanded properly in windows script -- spaces"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=bar baz', '-Xint']
        generator.scriptRelPath = "bin"
        when:
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        windowsScriptContent.contains(/set DEFAULT_JVM_OPTS="-Dfoo=bar baz" "-Xint"/)
    }

    def "defaultJvmOpts is expanded properly in windows script -- double quotes"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=b"ar baz', '-Xi""nt', '-Xpatho\\"logical']
        generator.scriptRelPath = "bin"
        when:
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        windowsScriptContent.contains(/set DEFAULT_JVM_OPTS="-Dfoo=b\"ar baz" "-Xi\"\"nt" "-Xpatho\\\"logical"/)
    }

    def "defaultJvmOpts is expanded properly in windows script -- backslashes and shell metacharacters"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=b\\ar baz', '-Xint%PATH%']
        generator.scriptRelPath = "bin"
        when:
        String windowsScriptContent = generator.generateWindowsScriptContent()
        then:
        windowsScriptContent.contains(/set DEFAULT_JVM_OPTS="-Dfoo=b\ar baz" "-Xint%%PATH%%"/)
    }

    def "defaultJvmOpts is expanded properly in unix script"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=bar', '-Xint']
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains('DEFAULT_JVM_OPTS=\'"-Dfoo=bar" "-Xint"\'')
    }

    def "defaultJvmOpts is expanded properly in unix script -- spaces"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=bar baz', '-Xint']
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=bar baz" "-Xint"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- double quotes"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=b"ar baz', '-Xi""nt']
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\"ar baz" "-Xi\"\"nt"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- single quotes"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=b\'ar baz', '-Xi\'\'n`t']
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=b'"'"'ar baz" "-Xi'"'"''"'"'n'"`"'t"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- backslashes and shell metacharacters"() {
        given:
        generator.defaultJvmOpts = ['-Dfoo=b\\ar baz', '-Xint$PATH']
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS='"-Dfoo=b\\ar baz" "-Xint/ + '\\$PATH' + /"'/)
    }

    def "defaultJvmOpts is expanded properly in unix script -- empty list"() {
        given:
        generator.scriptRelPath = "bin"
        when:
        String unixScriptContent = generator.generateUnixScriptContent()
        then:
        unixScriptContent.contains(/DEFAULT_JVM_OPTS=""/)
    }
}
