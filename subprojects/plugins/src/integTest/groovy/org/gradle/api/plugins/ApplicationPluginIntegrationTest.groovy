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
package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ApplicationPluginIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        createSampleProjectSetup()
    }

    def "can generate start scripts with expected content"() {
        when:
        succeeds('startScripts')

        then:
        File scriptOutputDir = file('build/scripts')
        File unixStartScript = new File(scriptOutputDir, 'sample')
        unixStartScript.exists()
        unixStartScript.canRead()
        unixStartScript.canExecute()
        String unixStartScriptContent = unixStartScript.text
        unixStartScriptContent.contains('##  sample start up script for UN*X')
        unixStartScriptContent.contains('DEFAULT_JVM_OPTS=""')
        unixStartScriptContent.contains('APP_NAME="sample"')
        unixStartScriptContent.contains('CLASSPATH=\$APP_HOME/lib/sample.jar')
        unixStartScriptContent.contains('exec "\$JAVACMD" "\${JVM_OPTS[@]}" -classpath "\$CLASSPATH" org.gradle.test.Main "\$@"')
        File windowsStartScript = new File(scriptOutputDir, 'sample.bat')
        windowsStartScript.exists()
        String windowsStartScriptContentText = windowsStartScript.text
        windowsStartScriptContentText.contains('@rem  sample startup script for Windows')
        windowsStartScriptContentText.contains('set DEFAULT_JVM_OPTS=')
        windowsStartScriptContentText.contains('set CLASSPATH=%APP_HOME%\\lib\\sample.jar')
        windowsStartScriptContentText.contains('"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SAMPLE_OPTS%  -classpath "%CLASSPATH%" org.gradle.test.Main %CMD_LINE_ARGS%')
    }

    def "can change template file for default start script generators"() {
        given:
        file('customUnixStartScript.txt') << '${applicationName} start up script for UN*X'
        file('customWindowsStartScript.txt') << '${applicationName} start up script for Windows'

        buildFile << """
startScripts {
    applicationName = 'myApp'
    unixStartScriptGenerator.template = new FileReader('customUnixStartScript.txt')
    windowsStartScriptGenerator.template = new FileReader('customWindowsStartScript.txt')
}
"""
        when:
        succeeds('startScripts')

        then:
        File scriptOutputDir = file('build/scripts')
        File unixStartScript = new File(scriptOutputDir, 'myApp')
        unixStartScript.exists()
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = new File(scriptOutputDir, 'myApp.bat')
        windowsStartScript.exists()
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    def "can use custom start script generators"() {
        given:
        buildFile << '''
startScripts {
    applicationName = 'myApp'
    unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
    windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
}

class CustomUnixStartScriptGenerator implements ScriptGenerator<JavaAppStartScriptGenerationDetails> {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            destination << "\${details.applicationName} start up script for UN*X"
        } finally {
            destination.close()
        }
    }
}

class CustomWindowsStartScriptGenerator implements ScriptGenerator<JavaAppStartScriptGenerationDetails> {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            destination << "\${details.applicationName} start up script for Windows"
        } finally {
            destination.close()
        }
    }
}
'''
        when:
        succeeds('startScripts')

        then:
        File scriptOutputDir = file('build/scripts')
        File unixStartScript = new File(scriptOutputDir, 'myApp')
        unixStartScript.exists()
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = new File(scriptOutputDir, 'myApp.bat')
        windowsStartScript.exists()
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    private void createSampleProjectSetup() {
        file('src/main/java/org/gradle/test/Main.java') << """
package org.gradle.test;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}
"""
        buildFile << """
apply plugin: 'application'

mainClassName = 'org.gradle.test.Main'
"""
        settingsFile << """
rootProject.name = 'sample'
"""
    }
}
