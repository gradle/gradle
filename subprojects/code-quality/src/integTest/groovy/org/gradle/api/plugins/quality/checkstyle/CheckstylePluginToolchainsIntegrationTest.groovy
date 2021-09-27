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

import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.junit.Assume

class CheckstylePluginToolchainsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file('config/checkstyle/checkstyle.xml') << """
<!DOCTYPE module PUBLIC
        "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
        "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
</module>
        """
        file('src/main/java/Dummy.java') << "class Dummy {}"
    }

    def "uses jdk from toolchains"() {
        given:
        def jdk = setupMatchingJdkAsToolchain {
            it.languageVersion > Jvm.current().javaVersion
        }
        buildFile << """
    plugins {
        id 'java'
        id 'checkstyle'
    }

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(${jdk.javaVersion.majorVersion})
        }
    }
"""
        when:
        def result = executer.withArgument("--info").withTasks("checkstyleMain").run()

        then:
        result.output.contains("Running checkstyle with toolchain '" + jdk.javaHome.absolutePath + "'.")
    }

    def "should not use toolchains if toolchain JDK matches current running JDK"() {
        given:
        setupMatchingJdkAsToolchain {
            it.javaHome.toAbsolutePath().toString() == Jvm.current().javaHome.absolutePath
        }
        buildFile << """
    plugins {
        id 'java'
        id 'checkstyle'
    }

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(${Jvm.current().javaVersion.majorVersion})
        }
    }
"""
        when:
        def result = executer.withArgument("--info").withTasks("checkstyleMain").run()

        then:
        !result.output.contains("Running checkstyle with toolchain")
    }

    Jvm setupMatchingJdkAsToolchain(Spec<? super JvmInstallationMetadata> jvmFilter) {
        Jvm jdk = AvailableJavaHomes.getAvailableJdk(jvmFilter)
        Assume.assumeNotNull(jdk)
        executer.beforeExecute {
            withArgument("-Porg.gradle.java.installations.paths=${jdk.javaHome.absolutePath}")
        }
        return jdk
    }

}
