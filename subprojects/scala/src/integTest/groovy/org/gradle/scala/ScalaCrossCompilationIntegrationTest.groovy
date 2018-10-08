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

package org.gradle.scala

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.internal.jvm.JavaInfo
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.TextUtil
import org.junit.Assume

@TargetVersions(["1.6", "1.7", "1.8"])
class ScalaCrossCompilationIntegrationTest extends MultiVersionIntegrationSpec {
    JavaVersion getJavaVersion() {
        JavaVersion.toVersion(MultiVersionIntegrationSpec.version)
    }

    JavaInfo getTarget() {
        return AvailableJavaHomes.getJdk(javaVersion)
    }

    def setup() {
        Assume.assumeTrue(target != null)
        // Java Compiler does not fork for joint compilation - therefore we cannot compile for a Java Version bigger than the current JVM
        Assume.assumeTrue(javaVersion.compareTo(JavaVersion.current()) <= 0)
        def java = TextUtil.escapeString(target.getJavaExecutable())
        def javaHome = TextUtil.escapeString(target.javaHome.absolutePath)

        buildFile << """
apply plugin: 'scala'
sourceCompatibility = ${MultiVersionIntegrationSpec.version}
targetCompatibility = ${MultiVersionIntegrationSpec.version}
${mavenCentralRepository()}

dependencies {
    compile 'org.scala-lang:scala-library:2.11.12'
}

tasks.withType(AbstractCompile) {
sourceCompatibility = ${MultiVersionIntegrationSpec.version}
targetCompatibility = ${MultiVersionIntegrationSpec.version}
    options.with {
        fork = true
        forkOptions.javaHome = file("$javaHome")
    }
}
tasks.withType(Test) {
    executable = "$java"
}
tasks.withType(JavaExec) {
    executable = "$java"
}

"""

        file("src/main/scala/Thing.java") << """
/** Some thing. */
public class Thing { }
"""

        file("src/main/scala/ScalaThing.scala") << """
/** Some scala thing. */
class ScalaThing { }
"""
    }

    def "can compile source and run JUnit tests using target Java version"() {
        given:
        buildFile << """
dependencies { testCompile 'junit:junit:4.12' }
"""

        file("src/test/scala/ThingTest.scala") << """
import _root_.org.junit.Test;
import _root_.org.junit.Assert._;

class ThingTest {
    @Test
    def verify() {
        assertTrue(System.getProperty("java.version").startsWith("${MultiVersionIntegrationSpec.version}."))
    }
}
"""

        expect:
        succeeds 'test'
        new ClassFile(scalaClassFile("Thing.class")).javaVersion == javaVersion

        // The Scala 2.11 compiler only produces Java 6 bytecode
        new ClassFile(scalaClassFile("ScalaThing.class")).javaVersion == JavaVersion.VERSION_1_6
        new ClassFile(classFile("scala", "test", "ThingTest.class")).javaVersion == JavaVersion.VERSION_1_6
    }
}
