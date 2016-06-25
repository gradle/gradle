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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import spock.lang.Unroll

class BuildScriptClasspathIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        executer.requireDaemon()
        executer.requireIsolatedDaemons()
    }

    def cleanup() {
        executer.withArguments("--stop").run()
    }

    @Unroll("jars on buildscript classpath can change (deleteIfExists: #deleteIfExists, loopNumber: #loopNumber)")
    def "jars on buildscript classpath can change"() {
        given:
        buildFile << '''
            buildscript {
                repositories { flatDir { dirs 'repo' }}
                dependencies { classpath name: 'test', version: '1.3-BUILD-SNAPSHOT' }
            }

            task hello << {
                println new org.gradle.test.BuildClass().message()
            }
        '''
        ArtifactBuilder builder = artifactBuilder()
        File jarFile = file("repo/test-1.3-BUILD-SNAPSHOT.jar")

        when:
        builder.sourceFile("org/gradle/test/BuildClass.java").createFile().text = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello world"; }
            }
        '''
        builder.buildJar(jarFile, deleteIfExists)

        then:
        Thread.sleep(sleepBefore)
        succeeds("hello")
        result.assertOutputContains("hello world")

        when:
        builder = artifactBuilder()
        builder.sourceFile("org/gradle/test/BuildClass.java").createFile().text = '''
            package org.gradle.test;
            public class BuildClass {
                public String message() { return "hello again"; }
            }
        '''
        builder.buildJar(jarFile, deleteIfExists)

        then:
        Thread.sleep(sleepBefore)
        succeeds("hello")
        result.assertOutputContains("hello again")

        where:
        deleteIfExists << [false, true] * 3
        loopNumber << (1..6).toList()
        sleepBefore = 1000L // fails on Linux when set to 1L
    }
}
