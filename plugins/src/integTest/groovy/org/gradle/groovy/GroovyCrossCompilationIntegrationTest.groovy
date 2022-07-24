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
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.internal.jvm.JavaInfo
import org.gradle.test.fixtures.file.ClassFile
import org.gradle.util.internal.TextUtil
import org.junit.Assume

@TargetVersions(["1.6", "1.7", "1.8"])
class GroovyCrossCompilationIntegrationTest extends MultiVersionIntegrationSpec {
    JavaVersion getJavaVersion() {
        JavaVersion.toVersion(MultiVersionIntegrationSpec.version)
    }

    JavaInfo getTarget() {
        return AvailableJavaHomes.getJdk(javaVersion)
    }

    def setup() {
        Assume.assumeTrue(target != null)
        def java = TextUtil.escapeString(target.getJavaExecutable())
        def javaHome = TextUtil.escapeString(target.javaHome.absolutePath)

        buildFile << """
apply plugin: 'groovy'
sourceCompatibility = ${MultiVersionIntegrationSpec.version}
targetCompatibility = ${MultiVersionIntegrationSpec.version}
${mavenCentralRepository()}

dependencies {
    implementation 'org.codehaus.groovy:groovy-all:2.4.10'
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

        file("src/main/groovy/Thing.java") << """
/** Some thing. */
public class Thing { }
"""

        file("src/main/groovy/GroovyThing.groovy") << """
/** Some groovy thing. */
class GroovyThing { }
"""
    }

    def "can compile source and run JUnit tests using target Java version"() {
        given:
        buildFile << """
dependencies { testImplementation 'org.spockframework:spock-core:1.0-groovy-2.4' }
"""

        file("src/test/groovy/ThingSpec.groovy") << """
class ThingSpec {
    def verify() {
        expect:
        System.getProperty("java.version").startsWith('${MultiVersionIntegrationSpec.version}.')
    }
}
"""

        expect:
        succeeds 'test'
        new ClassFile(groovyClassFile("Thing.class")).javaVersion == javaVersion
        new ClassFile(groovyClassFile("GroovyThing.class")).javaVersion == javaVersion
        new ClassFile(classFile("groovy", "test", "ThingSpec.class")).javaVersion == javaVersion
    }
}
