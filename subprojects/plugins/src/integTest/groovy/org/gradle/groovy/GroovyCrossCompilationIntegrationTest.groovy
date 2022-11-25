/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.groovy

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency

@TargetCoverage({ GroovyCoverage.SINCE_3_0 })
class GroovyCrossCompilationIntegrationTest extends MultiVersionIntegrationSpec implements JavaToolchainFixture {

    def "can compile source and run tests using Java #javaVersion for Groovy "() {
        def jdk = AvailableJavaHomes.getJdk(javaVersion)
        Assume.assumeTrue(jdk != null)

        buildFile << """
            apply plugin: "groovy"
            ${mavenCentralRepository()}

            ${javaPluginToolchainVersion(jdk)}

            dependencies {
                implementation "${groovyModuleDependency("groovy", version)}"
                testImplementation "org.spockframework:spock-core:${getSpockVersion(versionNumber)}"
            }

            test {
                useJUnitPlatform()
            }
        """

        file("src/main/groovy/Thing.java") << "public class Thing {}"
        file("src/main/groovy/GroovyThing.groovy") << "public class GroovyThing { def run() {} }"
        file("src/test/groovy/ThingSpec.groovy") << """
            class ThingSpec extends spock.lang.Specification {
                def verify() {
                    expect:
                    System.getProperty("java.version").startsWith("$javaVersion")
                }
            }
        """

        def groovyTarget = GroovyCoverage.getEffectiveTarget(versionNumber, jdk.javaVersion)

        when:
        withInstallations(jdk).run(":test")

        then:
        executedAndNotSkipped(":test")

        JavaVersion.forClass(groovyClassFile("Thing.class").bytes) == javaVersion
        JavaVersion.forClass(groovyClassFile("GroovyThing.class").bytes) == groovyTarget
        JavaVersion.forClass(classFile("groovy", "test", "ThingSpec.class").bytes) == groovyTarget

        where:
        javaVersion << JavaVersion.values().findAll { JavaVersion.VERSION_1_8 <= it }
    }

    private def getSpockVersion(VersionNumber groovyVersion) {
        return "2.3-groovy-${groovyVersion.major}.${groovyVersion.minor}"
    }
}
