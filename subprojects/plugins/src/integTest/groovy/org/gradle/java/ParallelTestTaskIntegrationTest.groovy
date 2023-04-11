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

package org.gradle.java

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.jvm.JavaInfo
import org.gradle.util.internal.TextUtil
import org.junit.Assume
import spock.lang.IgnoreIf

import java.util.concurrent.CountDownLatch

@IgnoreIf({ !GradleContextualExecuter.isParallel() })
class ParallelTestTaskIntegrationTest extends AbstractIntegrationSpec {
    String getVersion() {
        return "1.8"
    }

    JavaVersion getJavaVersion() {
        JavaVersion.toVersion(version)
    }

    JavaInfo getTarget() {
        return AvailableJavaHomes.getJdk(javaVersion)
    }


    def subprojects = ['a', 'b', 'c', 'd']

    def setup() {
        Assume.assumeTrue(target != null)

        def java = TextUtil.escapeString(target.getJavaExecutable())
        buildFile << """
import ${CountDownLatch.canonicalName}
def latch = new CountDownLatch(${subprojects.size()})
subprojects {
    apply plugin: 'java'
    java.sourceCompatibility = ${version}
    java.targetCompatibility = ${version}

    ${mavenCentralRepository()}
    dependencies { testImplementation 'junit:junit:4.13' }

    test {
        doFirst {
            println path + " waiting for other tests..."
            latch.countDown()
            latch.await()
            println path + " go"
        }

        executable = "$java"
    }
}
"""
        subprojects.each { subproject ->
            settingsFile << "include '$subproject'\n"
            file("${subproject}/src/test/java/ThingTest.java") << """
import org.junit.Test;
import static org.junit.Assert.*;

public class ThingTest {
    @Test
    public void verify() {
        assertTrue(System.getProperty("java.version").startsWith("${version}."));
    }
}
"""
        }
    }

    def "can run Test tasks in parallel when using different Java version from Gradle"() {
        expect:
        succeeds("test")
    }
}
