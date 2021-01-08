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

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue

class ApplicationPluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "installDist"
    }

    def setup() {
        createSampleProjectSetup()
    }

    def "can generate start scripts with minimal user configuration"() {
        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript()
        String unixStartScriptContent = unixStartScript.text
        unixStartScriptContent.contains('##  sample start up script for UN*X')
        unixStartScriptContent.contains('DEFAULT_JVM_OPTS=""')
        unixStartScriptContent.contains('APP_NAME="sample"')
        unixStartScriptContent.contains('CLASSPATH=\$APP_HOME/lib/sample.jar')
        !unixStartScriptContent.contains('MODULE_PATH=')
        unixStartScriptContent.contains('exec "\$JAVACMD" "\$@"')
        File windowsStartScript = assertGeneratedWindowsStartScript()
        String windowsStartScriptContentText = windowsStartScript.text
        windowsStartScriptContentText.contains('@rem  sample startup script for Windows')
        windowsStartScriptContentText.contains('set DEFAULT_JVM_OPTS=')
        windowsStartScriptContentText.contains('set CLASSPATH=%APP_HOME%\\lib\\sample.jar')
        !windowsStartScriptContentText.contains('set MODULE_PATH=')
        windowsStartScriptContentText.contains('"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SAMPLE_OPTS%  -classpath "%CLASSPATH%" org.gradle.test.Main %*')
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "can generate start scripts with module path"() {
        given:
        configureMainModule()

        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript()
        String unixStartScriptContent = unixStartScript.text
        unixStartScriptContent.contains('##  sample start up script for UN*X')
        unixStartScriptContent.contains('DEFAULT_JVM_OPTS=""')
        unixStartScriptContent.contains('APP_NAME="sample"')
        unixStartScriptContent.contains('CLASSPATH="\\\\\\"\\\\\\""')
        unixStartScriptContent.contains('MODULE_PATH=\$APP_HOME/lib/sample.jar')
        unixStartScriptContent.contains('exec "\$JAVACMD" "\$@"')
        File windowsStartScript = assertGeneratedWindowsStartScript()
        String windowsStartScriptContentText = windowsStartScript.text
        windowsStartScriptContentText.contains('@rem  sample startup script for Windows')
        windowsStartScriptContentText.contains('set DEFAULT_JVM_OPTS=')
        windowsStartScriptContentText.contains('set CLASSPATH=')
        windowsStartScriptContentText.contains('set MODULE_PATH=%APP_HOME%\\lib\\sample.jar')
        windowsStartScriptContentText.contains('"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SAMPLE_OPTS%  -classpath "%CLASSPATH%" --module-path "%MODULE_PATH%" --module org.gradle.test.main/org.gradle.test.Main %*')
    }

    def "can generate starts script generation with custom user configuration"() {
        given:
        buildFile << """
applicationName = 'myApp'
applicationDefaultJvmArgs = ["-Dgreeting.language=en", "-DappId=\${project.name - ':'}"]
"""

        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript('myApp')
        String unixStartScriptContent = unixStartScript.text
        unixStartScriptContent.contains('##  myApp start up script for UN*X')
        unixStartScriptContent.contains('APP_NAME="myApp"')
        unixStartScriptContent.contains('DEFAULT_JVM_OPTS=\'"-Dgreeting.language=en" "-DappId=sample"\'')
        unixStartScriptContent.contains('CLASSPATH=\$APP_HOME/lib/sample.jar')
        unixStartScriptContent.contains('exec "\$JAVACMD" "\$@"')
        File windowsStartScript = assertGeneratedWindowsStartScript('myApp.bat')
        String windowsStartScriptContentText = windowsStartScript.text
        windowsStartScriptContentText.contains('@rem  myApp startup script for Windows')
        windowsStartScriptContentText.contains('set DEFAULT_JVM_OPTS="-Dgreeting.language=en" "-DappId=sample"')
        windowsStartScriptContentText.contains('set CLASSPATH=%APP_HOME%\\lib\\sample.jar')
        windowsStartScriptContentText.contains('"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %MY_APP_OPTS%  -classpath "%CLASSPATH%" org.gradle.test.Main %*')
    }

    def "can change template file for default start script generators"() {
        given:
        file('customUnixStartScript.txt') << '${applicationName} start up script for UN*X'
        file('customWindowsStartScript.txt') << '${applicationName} start up script for Windows'

        buildFile << """
startScripts {
    applicationName = 'myApp'
    unixStartScriptGenerator.template = resources.text.fromFile(file('customUnixStartScript.txt'))
    windowsStartScriptGenerator.template = resources.text.fromFile(file('customWindowsStartScript.txt'))
}
"""
        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript('myApp')
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = assertGeneratedWindowsStartScript('myApp.bat')
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

class CustomUnixStartScriptGenerator implements ScriptGenerator {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        destination << "\${details.applicationName} start up script for UN*X"
    }
}

class CustomWindowsStartScriptGenerator implements ScriptGenerator {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        destination << "\${details.applicationName} start up script for Windows"
    }
}
'''
        when:
        succeeds('startScripts')

        then:
        File unixStartScript = assertGeneratedUnixStartScript('myApp')
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = assertGeneratedWindowsStartScript('myApp.bat')
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "can execute generated Unix start script"() {
        when:
        succeeds('installDist')

        then:
        file('build/install/sample').exists()

        when:
        runViaStartScript()

        then:
        outputContains('Hello World!')
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "can execute generated Unix start script using JAVA_HOME with spaces"() {
        given:
        def testJavaHome = file("javahome/java home with spaces")
        testJavaHome.createLink(Jvm.current().javaHome)

        when:
        succeeds('installDist')

        then:
        file('build/install/sample').exists()

        when:
        runViaUnixStartScriptWithJavaHome(testJavaHome.absolutePath)

        then:
        outputContains('Hello World!')

        cleanup:
        testJavaHome.usingNativeTools().deleteDir() //remove symlink
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "java PID equals script PID"() {
        given:
        succeeds('installDist')
        def binFile = file('build/install/sample/bin/sample')
        binFile.text = """echo Script PID: \$\$

$binFile.text
"""

        when:
        runViaStartScript()
        def pids = result.output.findAll(/PID: \d+/)

        then:
        assert pids.size() == 2
        assert pids[0] == pids[1]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "can execute generated Windows start script"() {
        when:
        succeeds('installDist')

        then:
        file('build/install/sample').exists()

        when:
        runViaStartScript()

        then:
        outputContains('Hello World!')
    }

    ExecutionResult runViaUnixStartScript(TestFile startScriptDir) {
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine './sample'
    environment JAVA_OPTS: ''
}
"""
        return succeeds('execStartScript')
    }

    ExecutionResult runViaUnixStartScriptWithJavaHome(String javaHome) {
        TestFile startScriptDir = file('build/install/sample/bin')

        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$startScriptDir.canonicalPath'
    commandLine './sample'
    environment JAVA_HOME: "$javaHome"
    environment JAVA_OPTS: ''
}
"""
        return succeeds('execStartScript')
    }

    ExecutionResult runViaWindowsStartScript(TestFile startScriptDir) {
        String escapedStartScriptDir = startScriptDir.canonicalPath.replaceAll('\\\\', '\\\\\\\\')
        buildFile << """
task execStartScript(type: Exec) {
    workingDir '$escapedStartScriptDir'
    commandLine 'cmd', '/c', 'sample.bat'
    environment JAVA_OPTS: ''
}
"""
        return succeeds('execStartScript')
    }

    def "compile only dependencies are not included in distribution"() {
        given:
        mavenRepo.module('org.gradle.test', 'compile', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()

        and:
        buildFile << """
repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    implementation 'org.gradle.test:compile:1.0'
    compileOnly 'org.gradle.test:compileOnly:1.0'
}
"""
        when:
        run "installDist"

        then:
        file('build/install/sample/lib').allDescendants() == ['sample.jar', 'compile-1.0.jar'] as Set
    }

    def "executables can be placed at the root of the distribution"() {
        given:
        buildFile << """
executableDir = ''
"""
        when:
        run "installDist"

        then:
        file('build/install/sample/sample').exists()
        file('build/install/sample/sample.bat').exists()

        when:
        runViaStartScript(file('build/install/sample'))
        then:
        outputContains("Hello World")
    }

    def "executables can be placed in a custom directory"() {
        given:
        buildFile << """
executableDir = 'foo/bar'
"""
        when:
        run "installDist"

        then:
        file('build/install/sample/foo/bar/sample').exists()
        file('build/install/sample/foo/bar/sample.bat').exists()

        when:
        runViaStartScript(file('build/install/sample/foo/bar'))
        then:
        outputContains("Hello World")
    }

    def "includes transitive implementation dependencies in distribution"() {
        mavenRepo.module('org.gradle.test', 'implementation', '1.0').publish()

        given:
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenRepo.uri' }
                }
            }
        """

        file('settings.gradle') << "include 'utils', 'core'"
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'application'

            dependencies {
               implementation project(':utils')
            }
        '''
        file('utils/build.gradle') << '''
            apply plugin: 'java-library'

            dependencies {
                api project(':core')
            }
        '''
        file('core/build.gradle') << '''
            apply plugin: 'java-library'

            dependencies {
                implementation 'org.gradle.test:implementation:1.0'
            }
        '''

        when:
        run "installDist"

        then:
        file('build/install/sample/lib').allDescendants() == ['sample.jar', 'utils.jar', 'core.jar', 'implementation-1.0.jar'] as Set

        and:
        unixClasspath('sample') == ['sample.jar', 'utils.jar', 'core.jar', 'implementation-1.0.jar'] as Set
        windowsClasspath('sample') == ['sample.jar', 'utils.jar', 'core.jar', 'implementation-1.0.jar'] as Set
    }

    def "includes transitive runtime dependencies in runtime classpath"() {
        mavenRepo.module('org.gradle.test', 'implementation', '1.0').publish()

        given:
        buildFile << """
        allprojects {
            repositories {
                maven { url '$mavenRepo.uri' }
            }
            apply plugin: 'java'
        }
        """

        file('settings.gradle') << "include 'utils', 'core', 'foo', 'bar'"
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'application'

            dependencies {
               implementation project(':utils')
            }

            task printRunClasspath {
                doLast {
                    println run.classpath.collect{ it.name }.join(',')
                }
            }

        '''
        file('utils/build.gradle') << '''
            apply plugin: 'java-library'

            dependencies {
               api project(':core')
               runtimeOnly project(':foo')
            }
        '''
        file('core/build.gradle') << '''
apply plugin: 'java-library'

dependencies {
    implementation 'org.gradle.test:implementation:1.0'
    runtimeOnly project(':bar')
}
        '''

        when:
        run "printRunClasspath"

        then:
        outputContains('utils.jar,core.jar,foo.jar,implementation-1.0.jar,bar.jar')
    }

    def "includes transitive implementation dependencies in test runtime classpath"() {
        mavenRepo.module('org.gradle.test', 'implementation', '1.0').publish()

        given:
        buildFile << """
        allprojects {
            repositories {
                maven { url '$mavenRepo.uri' }
            }
            apply plugin: 'java'
        }
        """

        file('settings.gradle') << "include 'utils', 'core', 'foo', 'bar'"
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'application'

            dependencies {
               implementation project(':utils')
            }

            task printTestClasspath {
                doLast {
                    println test.classpath.collect{ it.name }.join(',')
                }
            }

        '''
        file('utils/build.gradle') << '''
            apply plugin: 'java-library'

            dependencies {
                api project(':core')
                runtimeOnly project(':foo')
            }
        '''
        file('core/build.gradle') << '''
apply plugin: 'java-library'

dependencies {
    implementation 'org.gradle.test:implementation:1.0'
    runtimeOnly project(':bar')
}
        '''

        when:
        run "printTestClasspath"

        then:
        outputContains('utils.jar,core.jar,foo.jar,implementation-1.0.jar,bar.jar')
    }

    private Set<String> unixClasspath(String baseName) {
        String[] lines = file("build/install/$baseName/bin/$baseName")
        (lines.find { it.startsWith 'CLASSPATH='} - 'CLASSPATH=').split(':').collect([] as Set) { it - '$APP_HOME/lib/'}
    }

    private Set<String> windowsClasspath(String baseName) {
        String[] lines = file("build/install/$baseName/bin/${baseName}.bat")
        (lines.find { it.startsWith 'set CLASSPATH='} - 'set CLASSPATH=').split(';').collect([] as Set) { it - '%APP_HOME%\\lib\\'}
    }

    def "can use APP_HOME in DEFAULT_JVM_OPTS with custom start script"() {
        given:
        buildFile << """
applicationDefaultJvmArgs = ["-DappHomeSystemProp=REPLACE_THIS_WITH_APP_HOME"]

startScripts {
    doLast {
        unixScript.text = unixScript.text.replace("REPLACE_THIS_WITH_APP_HOME", "'\\\$APP_HOME'")
        windowsScript.text = windowsScript.text.replace("REPLACE_THIS_WITH_APP_HOME", '%APP_HOME%')
    }
}
"""
        when:
        succeeds('installDist')
        and:
        runViaStartScript()

        then:
        outputContains("App Home: ${file('build/install/sample').absolutePath}")
    }

    ExecutionResult runViaStartScript(TestFile startScriptDir = file('build/install/sample/bin')) {
        OperatingSystem.current().isWindows() ? runViaWindowsStartScript(startScriptDir) : runViaUnixStartScript(startScriptDir)
    }

    private void createSampleProjectSetup() {
        createMainClass()
        populateBuildFile()
        populateSettingsFile()
    }

    private void createMainClass() {
        generateMainClass """
            System.out.println("App Home: " + System.getProperty("appHomeSystemProp"));
            System.out.println("App PID: " + java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
            System.out.println("Hello World!");
        """
    }

    private void generateMainClass(String mainMethodBody) {
        file('src/main/java/org/gradle/test/Main.java').text = """
package org.gradle.test;

public class Main {
    public static void main(String[] args) {
        $mainMethodBody
    }
}
"""
    }

    private void populateBuildFile() {
        buildFile << """
apply plugin: 'application'

application {
    mainClass = 'org.gradle.test.Main'
}
"""
    }

    private void configureMainModule() {
        file("src/main/java/module-info.java") << "module org.gradle.test.main { requires java.management; }"
        buildFile << """
application {
    mainModule.set('org.gradle.test.main')
}
"""
    }

    private void populateSettingsFile() {
        settingsFile << """
rootProject.name = 'sample'
"""
    }

    private File assertGeneratedUnixStartScript(String filename = 'sample') {
        File startScript = getGeneratedStartScript(filename)
        assert startScript.exists()
        assert startScript.canRead()
        assert startScript.canExecute()
        startScript
    }

    private File assertGeneratedWindowsStartScript(String filename = 'sample.bat') {
        File startScript = getGeneratedStartScript(filename)
        assert startScript.exists()
        startScript
    }

    private File getGeneratedStartScript(String filename) {
        File scriptOutputDir = file('build/scripts')
        new File(scriptOutputDir, filename)
    }

    @Issue("https://github.com/gradle/gradle/issues/1923")
    def "not up-to-date if classpath changes"() {
        given:
        succeeds("startScripts")

        when:
        buildFile << """
            // Set a version so the jar is created with a different name
            // This should cause us to regenerated the script
            version = "3.0"
        """
        and:
        succeeds("startScripts")

        then:
        executedAndNotSkipped(":startScripts")

        and:
        succeeds("startScripts")

        then:
        skipped(":startScripts")
    }

    def "start script generation depends on jar creation"() {
        when:
        succeeds('startScripts')

        then:
        executed ":processResources", ":classes", ":jar"
    }

    @Issue("https://github.com/gradle/gradle/issues/4627")
    @Requires(TestPrecondition.NOT_WINDOWS)
    def "distribution not in root directory has correct permissions set"() {
        given:
        buildFile << """
            distributions {
                main {
                    contents {
                        into "/not-the-root"
                    }
                }
            }
        """

        when:
        succeeds("installDist")

        then:
        file('build/install/sample/').allDescendants() == ["not-the-root/bin/sample", "not-the-root/bin/sample.bat", "not-the-root/lib/sample.jar"] as Set
        assert file("build/install/sample/not-the-root/bin/sample").permissions == "rwxr-xr-x"
    }

    def "runs the classes folder for traditional applications"() {
        when:
        succeeds("run")

        then:
        executed(':compileJava', ':processResources', ':classes', ':run')
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "runs the jar for modular applications"() {
        given:
        configureMainModule()

        when:
        succeeds("run")

        then:
        executed(':compileJava', ':processResources', ':classes', ':jar', ':run')
    }
}
