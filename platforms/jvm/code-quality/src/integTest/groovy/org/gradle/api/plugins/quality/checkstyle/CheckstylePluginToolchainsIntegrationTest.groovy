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

import org.gradle.api.JavaVersion
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.quality.integtest.fixtures.CheckstyleCoverage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.hamcrest.Matcher
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.util.Matchers.containsLine
import static org.hamcrest.CoreMatchers.containsString

@TargetCoverage({ CheckstyleCoverage.getSupportedVersionsByJdk() })
class CheckstylePluginToolchainsIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

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

    @Issue("https://github.com/gradle/gradle/issues/21353")
    def "uses current jdk if checkstyle plugin is not applied"() {
        given:
        goodCode()
        writeDummyConfig()
        setupExecutorForToolchains()
        writeBuildFileWithoutApplyingCheckstylePlugin()
        buildFile << """
            Map<String, String> excludeProperties(String group, String module) {
                return ["group": group, "module": module]
            }
            Configuration configuration = configurations.create("checkstyle")
            configuration.exclude(excludeProperties("ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant"))
            configuration.exclude(excludeProperties("org.apache.ant", "ant-launcher"))
            configuration.exclude(excludeProperties("org.slf4j", "slf4j-api"))
            configuration.exclude(excludeProperties("org.slf4j", "jcl-over-slf4j"))
            configuration.exclude(excludeProperties("org.slf4j", "log4j-over-slf4j"))
            configuration.exclude(excludeProperties("commons-logging", "commons-logging"))
            configuration.exclude(excludeProperties("log4j", "log4j"))
            dependencies.add("checkstyle", dependencies.create("com.puppycrawl.tools:checkstyle:$version"))
            FileCollection checkstyleFileCollection = configuration

            tasks.register("myCheckstyle", Checkstyle) {
                checkstyleClasspath = checkstyleFileCollection
                configFile = file("\$projectDir/config/checkstyle/checkstyle.xml")
                configDirectory = file("\$projectDir/config/checkstyle")
                source = fileTree("\$projectDir/src/main")
                classpath = objects.fileCollection().from("\$buildDir/classes")
            }
"""

        when:
        succeeds("myCheckstyle")

        then:
        outputContains("Running checkstyle with toolchain '${Jvm.current().javaHome.absolutePath}'.")
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
                        new File(it.forkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.jvmOptions.minHeapSize == "128m" &&
                        it.forkOptions.jvmOptions.maxHeapSize == "256m"
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

        file("build/reports/checkstyle/main.html").assertExists()
    }

    @Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "explicit locale")
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
        failure.assertHasResolutions(SCAN)
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.gradle.class2"))

        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class1"))
        file("build/reports/checkstyle/main.html").assertContents(containsClass("org.gradle.class2"))
    }

    @Issue("https://github.com/gradle/gradle/issues/34759")
    @Requires(IntegTestPreconditions.Java8HomeAvailable)
    def "fails with helpful error when using incompatible Java 8 toolchain with Checkstyle 10.x"() {
        given:
        goodCode()
        writeDummyConfig()
        def java8 = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_8)
        withInstallations(java8)

        buildFile << """
            plugins {
                id 'java'
                id 'checkstyle'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
            }

            checkstyle {
                toolVersion = '10.24.0'  // Requires Java 11+, incompatible with Java 8 toolchain
            }

            repositories {
                ${mavenCentralRepository()}
            }
        """

        when:
        fails("checkstyleMain")

        then:
        failure.assertHasDescription("Execution failed for task ':checkstyleMain'.")
        failure.assertHasCause("Checkstyle 10.24.0 is not compatible with the configured Java version (Java 8)")
        failure.assertThatCause(containsString("Use a newer Java toolchain for Checkstyle"))
        failure.assertThatCause(containsString("tasks.withType(Checkstyle).configureEach"))
        failure.assertThatCause(containsString("languageVersion = JavaLanguageVersion.of(<appropriate-version>)"))
        failure.assertThatCause(containsString("Use a Checkstyle version compatible with Java 8"))
        failure.assertThatCause(containsString("https://checkstyle.org/releasenotes.html"))
    }

    Jvm setupExecutorForToolchains() {
        Jvm jdk = AvailableJavaHomes.getDifferentVersion(new Spec<JvmInstallationMetadata>() {
            @Override
            boolean isSatisfiedBy(JvmInstallationMetadata metadata) {
                metadata.getLanguageVersion() >= CheckstyleCoverage.getMinimumSupportedJdkVersion(versionNumber)
            }
        })
        withInstallations(jdk)
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

    private void writeBuildFileWithoutApplyingCheckstylePlugin() {
        buildFile << """
            plugins {
                id 'groovy'
                id 'java'
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
        configureJavaPluginToolchainVersion(jvm)
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
