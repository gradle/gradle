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
import org.gradle.jvm.application.scripts.ScriptGenerator
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class StartScriptGeneratorTest extends Specification {

    private static final String APP_NAME = 'Gradle'
    private static final String OPTS_ENV_VAR = 'GRADLE_OPTS'
    private static final String EXIT_ENV_VAR = 'GRADLE_EXIT_CONSOLE'
    private static final String MAIN_CLASSNAME = 'org.gradle.launcher.GradleMain'
    private static final Iterable<String> DEFAULT_JVM_OPTS = ['-Xmx1024m']
    private static final Iterable<String> CLASSPATH = ['libs/gradle.jar']
    private static final Iterable<String> MODULE_PATH = []
    private static final String SCRIPT_REL_PATH = 'bin/gradle'
    private static final String APP_NAME_SYS_PROP = 'org.gradle.appname'

    ScriptGenerator unixStartScriptGenerator = Mock()
    ScriptGenerator windowsStartScriptGenerator = Mock()
    StartScriptGenerator.UnixFileOperation unixFileOperation = Mock()
    StartScriptGenerator startScriptGenerator = new StartScriptGenerator(unixStartScriptGenerator, windowsStartScriptGenerator, unixFileOperation)
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        populateStartScriptGenerator()
    }

    def "can generate Unix script"() {
        given:
        TestFile script = temporaryFolder.file('unix.sh')

        when:
        startScriptGenerator.generateUnixScript(script)

        then:
        1 * unixStartScriptGenerator.generateScript(createJavaAppStartScriptGenerationDetails(), _ as Writer)
        0 * windowsStartScriptGenerator.generateScript(_, _)
        1 * unixFileOperation.createExecutablePermission(script)
    }

    def "can generate Windows script"() {
        given:
        TestFile script = temporaryFolder.file('windows.bat')

        when:
        startScriptGenerator.generateWindowsScript(script)

        then:
        1 * windowsStartScriptGenerator.generateScript(createJavaAppStartScriptGenerationDetails(), _ as Writer)
        0 * unixStartScriptGenerator.generateScript(_, _)
        0 * unixFileOperation.createExecutablePermission(script)
    }

    private void populateStartScriptGenerator() {
        startScriptGenerator.applicationName = APP_NAME
        startScriptGenerator.optsEnvironmentVar = OPTS_ENV_VAR
        startScriptGenerator.exitEnvironmentVar = EXIT_ENV_VAR
        startScriptGenerator.mainClassName = MAIN_CLASSNAME
        startScriptGenerator.defaultJvmOpts = DEFAULT_JVM_OPTS
        startScriptGenerator.classpath = CLASSPATH
        startScriptGenerator.modulePath = MODULE_PATH
        startScriptGenerator.scriptRelPath = SCRIPT_REL_PATH
        startScriptGenerator.appNameSystemProperty = APP_NAME_SYS_PROP
    }

    private JavaAppStartScriptGenerationDetails createJavaAppStartScriptGenerationDetails() {
        return new DefaultJavaAppStartScriptGenerationDetails(APP_NAME, OPTS_ENV_VAR, EXIT_ENV_VAR, MAIN_CLASSNAME, DEFAULT_JVM_OPTS, CLASSPATH, MODULE_PATH, SCRIPT_REL_PATH, APP_NAME_SYS_PROP)
    }
}
