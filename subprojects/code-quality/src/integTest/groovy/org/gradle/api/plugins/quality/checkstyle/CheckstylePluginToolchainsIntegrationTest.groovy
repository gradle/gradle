/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.jvm.Jvm
import org.gradle.quality.integtest.fixtures.CheckstyleCoverage
import org.hamcrest.Matcher

import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.CoreMatchers.containsString
import static org.junit.Assume.assumeNotNull

@TargetCoverage({ CheckstyleCoverage.getSupportedVersionsByJdk() })
class CheckstylePluginToolchainsIntegrationTest extends MultiVersionIntegrationSpec {

    def setup() {
        executer.withArgument("--info")
    }

    def "uses jdk from toolchains set through java plugin"() {
        given:
        goodCode()
        writeDummyConfig()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromJavaPlugin(jdk)

        when:
        succeeds("checkstyleMain")

        then:
        outputContains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "uses jdk from toolchains set through checkstyle task"() {
        given:
        goodCode()
        writeDummyConfig()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromCheckstyleTask(jdk)

        when:
        succeeds("checkstyleMain")

        then:
        outputContains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
    }

    def "uses current jdk if not specified otherwise"() {
        given:
        goodCode()
        writeDummyConfig()
        writeBuildFile()

        when:
        succeeds("checkstyleMain")

        then:
        outputContains("Running checkstyle with toolchain '${Jvm.current().javaHome.absolutePath}'")
    }

    def "respects memory options settings"() {
        given:
        goodCode()
        writeDummyConfig()
        writeBuildFile()

        when:
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm

            tasks.named('checkstyleMain').configure {
                minHeapSize.set("128m")
                maxHeapSize.set("256m")

                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find {
                        new File(it.forkOptions.javaForkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.javaForkOptions.minHeapSize == "128m" &&
                        it.forkOptions.javaForkOptions.maxHeapSize == "256m"
                    }
                }
            }
        """

        then:
        succeeds("checkstyleMain")
    }

    def "analyze good code with the toolchain JDK"() {
        goodCode()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromJavaPlugin(jdk)
        writeConfigFileWithTypeName()

        expect:
        succeeds('checkstyleMain')
        outputContains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.Class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.Class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.Class2"))
    }

    def "analyze bad code with the toolchain JDK"() {
        executer.withDefaultLocale(new Locale('en'))
        badCode()
        def jdk = setupExecutorForToolchains()
        writeBuildFileWithToolchainsFromJavaPlugin(jdk)
        writeConfigFileWithTypeName()

        expect:
        fails("checkstyleMain")
        outputContains("Running checkstyle with toolchain '${jdk.javaHome.absolutePath}'.")
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertHasErrorOutput("Name 'class1' must match pattern")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    Jvm setupExecutorForToolchains() {
        Jvm jdk = AvailableJavaHomes.getDifferentVersion()
        assumeNotNull(jdk)
        executer.withArgument("-Porg.gradle.java.installations.paths=${jdk.javaHome.absolutePath}")
        return jdk
    }

    private void writeBuildFile() {
        buildFile << """
    plugins {
        id 'groovy'
        id 'java'
        id 'checkstyle'
    }

    checkstyle {
        toolVersion = '$version'
    }

    repositories {
        ${mavenCentralRepository()}
    }

    dependencies {
        implementation localGroovy()
    }
"""
    }

    private void writeBuildFileWithToolchainsFromJavaPlugin(Jvm jvm) {
        writeBuildFile()
        buildFile << """
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
        }
    }
"""
    }

    private void writeBuildFileWithToolchainsFromCheckstyleTask(Jvm jvm) {
        writeBuildFile()
        buildFile << """
    tasks.withType(Checkstyle) {
        javaLauncher = javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(${jvm.javaVersion.majorVersion})
        }
    }
"""
    }

    private void writeDummyConfig() {
        file('config/checkstyle/checkstyle.xml') << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
</module>
        """
    }

    private void writeConfigFileWithTypeName() {
        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="SuppressionFilter">
        <property name="file" value="\${config_loc}/suppressions.xml"/>
    </module>
    <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
        """

        file("config/checkstyle/suppressions.xml") << """
<!DOCTYPE suppressions PUBLIC
    "-//Puppy Crawl//DTD Suppressions 1.1//EN"
    "http://www.puppycrawl.com/dtds/suppressions_1_1.dtd">

<suppressions>
    <suppress checks="TypeName"
          files="bad_name.java"/>
</suppressions>
        """
    }

    private void goodCode() {
        file('src/main/java/org/gradle/Class1.java') << 'package org.gradle; class Class1 { }'
        file('src/main/groovy/org/gradle/Class2.java') << 'package org.gradle; class Class2 { }'
    }

    private void badCode() {
        file("src/main/java/org/gradle/class1.java") << "package org.gradle; class class1 { }"
        file("src/main/groovy/org/gradle/class2.java") << "package org.gradle; class class2 { }"
    }

    private static Matcher<String> containsClass(String className) {
        containsLine(containsString(className.replace(".", File.separator)))
    }

}
