/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ScriptExecuter
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.TextUtil

import static org.hamcrest.Matchers.startsWith

class ApplicationIntegrationTest extends AbstractIntegrationSpec{

    def canUseEnvironmentVariableToPassMultipleOptionsToJvmWhenRunningScript() {
        file("build.gradle") << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'application'
'''
        file('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
        if (!"value".equals(System.getProperty("testValue"))) {
            throw new RuntimeException("Expected system property not specified");
        }
        if (!"some value".equals(System.getProperty("testValue2"))) {
            throw new RuntimeException("Expected system property not specified");
        }
        if (!"some value".equals(System.getProperty("testValue3"))) {
            throw new RuntimeException("Expected system property not specified");
        }
    }
}
'''

        when:
        run 'install'

        def builder = new ScriptExecuter()
        builder.workingDir file('build/install/application/bin')
        builder.executable "application"
        if (OperatingSystem.current().windows) {
            builder.environment('APPLICATION_OPTS', '-DtestValue=value -DtestValue2="some value" -DtestValue3="some value"')
        } else {
            builder.environment('APPLICATION_OPTS', '-DtestValue=value -DtestValue2=\'some value\' -DtestValue3=some\\ value')
        }

        def result = builder.run()

        then:
        result.assertNormalExitValue()
    }

    def canUseDefaultJvmArgsToPassMultipleOptionsToJvmWhenRunningScript() {
        file("build.gradle") << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'application'
applicationDefaultJvmArgs = ['-DtestValue=value', '-DtestValue2=some value', '-DtestValue3=some value']
'''
        file('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
        if (!"value".equals(System.getProperty("testValue"))) {
            throw new RuntimeException("Expected system property not specified");
        }
        if (!"some value".equals(System.getProperty("testValue2"))) {
            throw new RuntimeException("Expected system property not specified");
        }
        if (!"some value".equals(System.getProperty("testValue3"))) {
            throw new RuntimeException("Expected system property not specified");
        }
    }
}
'''

        when:
        run 'install'

        def builder = new ScriptExecuter()
        builder.workingDir file('build/install/application/bin')
        builder.executable "application"

        def result = builder.run()

        then:
        result.assertNormalExitValue()
    }

    def canUseBothDefaultJvmArgsAndEnvironmentVariableToPassOptionsToJvmWhenRunningScript() {
        file("build.gradle") << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'application'
applicationDefaultJvmArgs = ['-Dvar1=value1', '-Dvar2=some value2']
'''
        file('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
        if (!"value1".equals(System.getProperty("var1"))) {
            throw new RuntimeException("Expected system property not specified");
        }
        if (!"some value2".equals(System.getProperty("var2"))) {
            throw new RuntimeException("Expected system property not specified");
        }
        if (!"value3".equals(System.getProperty("var3"))) {
            throw new RuntimeException("Expected system property not specified");
        }
    }
}
'''

        when:
        run 'install'

        def builder = new ScriptExecuter()
        builder.workingDir file('build/install/application/bin')
        builder.executable "application"
        builder.environment('APPLICATION_OPTS', '-Dvar3=value3')

        def result = builder.run()

        then:
        result.assertNormalExitValue()
    }

    def canUseDefaultJvmArgsToPassMultipleOptionsWithShellMetacharactersToJvmWhenRunningScript() {
        //even in single-quoted multi-line strings, backslashes must still be quoted
        file("build.gradle") << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'application'
applicationDefaultJvmArgs = ['-DtestValue=value',
                             /-DtestValue2=s\\o"me val'ue/ + '$PATH',
                             /-DtestValue3=so\\"me value%PATH%/,
                            ]
'''
        file('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
        if (!"value".equals(System.getProperty("testValue"))) {
            throw new RuntimeException("Expected system property not specified (testValue)");
        }
        if (!"s\\\\o\\"me val'ue$PATH".equals(System.getProperty("testValue2"))) {
            throw new RuntimeException("Expected system property not specified (testValue2)");
        }
        if (!"so\\\\\\"me value%PATH%".equals(System.getProperty("testValue3"))) {
            throw new RuntimeException("Expected system property not specified (testValue3)");
        }
    }
}
'''

        when:
        run 'install'

        def builder = new ScriptExecuter()
        builder.workingDir file('build/install/application/bin')
        builder.executable "application"

        def result = builder.run()

        then:
        result.assertNormalExitValue()
    }

    def canUseDefaultJvmArgsInRunTask() {
            file("build.gradle") << '''
    apply plugin: 'application'
    mainClassName = 'org.gradle.test.Main'
    applicationName = 'application'
    applicationDefaultJvmArgs = ['-Dvar1=value1', '-Dvar2=value2']
    '''
            file('src/main/java/org/gradle/test/Main.java') << '''
    package org.gradle.test;

    class Main {
        public static void main(String[] args) {
            if (!"value1".equals(System.getProperty("var1"))) {
                throw new RuntimeException("Expected system property not specified (var1)");
            }
            if (!"value2".equals(System.getProperty("var2"))) {
                throw new RuntimeException("Expected system property not specified (var2)");
            }
        }
    }
    '''

            expect:
            run 'run'
        }


    def "can customize application name"() {
        file('settings.gradle') << 'rootProject.name = "application"'
        file('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
applicationName = 'mega-app'
'''
        file('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
    }
}
'''

        when:
        run 'install', 'distZip', 'distTar'

        then:
        def installDir = file('build/install/mega-app')
        installDir.assertIsDir()
        checkApplicationImage('mega-app', installDir)

        def distZipFile = file('build/distributions/mega-app.zip')
        distZipFile.assertIsFile()

        def distZipDir = file('build/unzip')
        distZipFile.usingNativeTools().unzipTo(distZipDir)
        checkApplicationImage('mega-app', distZipDir.file('mega-app'))

        def distTarFile = file('build/distributions/mega-app.tar')
        distTarFile.assertIsFile()

        def distTarDir = file('build/untar')
        distTarFile.usingNativeTools().untarTo(distTarDir)
        checkApplicationImage('mega-app', distTarDir.file('mega-app'))
    }

    def "check distribution contents when all defaults used"() {
        file('settings.gradle') << 'rootProject.name = "application"'
        file('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
'''
        file('src/main/java/org/gradle/test/Main.java') << '''
package org.gradle.test;

class Main {
    public static void main(String[] args) {
    }
}
'''

        when:
        run 'install', 'distZip', 'distTar'

        then:
        def installDir = file('build/install/application')
        installDir.assertIsDir()
        checkApplicationImage('application', installDir)
        
        def distZipFile = file('build/distributions/application.zip')
        distZipFile.assertIsFile()
        
        def distZipDir = file('build/unzip')
        distZipFile.usingNativeTools().unzipTo(distZipDir)
        checkApplicationImage('application', distZipDir.file('application'))
        
        def distTarFile = file('build/distributions/application.tar')
        distTarFile.assertIsFile()
		
        def distTarDir = file('build/untar')
        distTarFile.usingNativeTools().untarTo(distTarDir)
        checkApplicationImage('application', distTarDir.file('application'))
    }

    def "installApp complains if install directory exists and doesn't look like previous install"() {
        file('build.gradle') << '''
apply plugin: 'application'
mainClassName = 'org.gradle.test.Main'
installApp.destinationDir = buildDir
'''

        when:
        runAndFail 'installApp'

        then:
        result.assertThatCause(startsWith("The specified installation directory '${file('build')}' is neither empty nor does it contain an installation"))
    }

    def "startScripts respect OS dependent line separators"() {
        file('build.gradle') << '''
    apply plugin: 'application'
    applicationName = 'mega-app'
    mainClassName = 'org.gradle.test.Main'
    installApp.destinationDir = buildDir
    '''

        when:
        run 'startScripts'

        then:
        File generatedWindowsStartScript = file("build/scripts/mega-app.bat")
        generatedWindowsStartScript.exists()
        assertLineSeparators(generatedWindowsStartScript, TextUtil.windowsLineSeparator, 90);

        File generatedLinuxStartScript = file("build/scripts/mega-app")
        generatedLinuxStartScript.exists()
        assertLineSeparators(generatedLinuxStartScript, TextUtil.unixLineSeparator, 164);
        assertLineSeparators(generatedLinuxStartScript, TextUtil.windowsLineSeparator, 1)

        file("build/scripts/mega-app").exists()
    }

    private void checkApplicationImage(String applicationName, TestFile installDir) {
        installDir.file("bin/${applicationName}").assertIsFile()
        installDir.file("bin/${applicationName}.bat").assertIsFile()
        installDir.file("lib/application.jar").assertIsFile()
        
        def builder = new ScriptExecuter()
        builder.workingDir installDir.file('bin')
        builder.executable applicationName
        builder.standardOutput = new ByteArrayOutputStream()
        builder.errorOutput = new ByteArrayOutputStream()
        
        def result = builder.run()
        result.assertNormalExitValue()
    }

    def assertLineSeparators(TestFile testFile, String lineSeparator, expectedLineCount) {
        assert testFile.text.split(lineSeparator).length == expectedLineCount
        true
    }
}
