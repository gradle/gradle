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

package org.gradle.testkit.runner.enduser

import groovy.transform.NotYetImplemented
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import spock.lang.Issue

@NonCrossVersion
@NoDebug
class NebulaTestCheckstyleEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def setup() {
        buildFile << """
            plugins {
                id 'groovy'
            }
            repositories {
                jcenter()
            }
            dependencies {
                compile gradleApi()
                compile 'com.netflix.nebula:nebula-test:latest.release'
            }
        """

        file("src/test/groovy/CheckstyleIntegrationSpec.groovy") << """
import com.google.common.io.Files
import nebula.test.IntegrationSpec

class CheckstyleIntegrationSpec extends IntegrationSpec {
    def 'task runs successfully'() {
        setup:
        buildFile << '''
apply plugin: 'java'
apply plugin: 'checkstyle'

repositories {
    mavenCentral()
}

tasks.withType(Checkstyle) {
    ignoreFailures = true
}

dependencies {
    testCompile 'junit:junit:4.11'
}
'''
        javaFile()
        testFile()
        checkFile()

        when:
        def result = runTasksSuccessfully('clean', 'check')

        then:
        result.wasExecuted(':checkstyleMain')
        result.wasExecuted(':checkstyleTest')

        new File(projectDir, 'build/reports/checkstyle/main.xml').exists()
        new File(projectDir, 'build/reports/checkstyle/test.xml').exists()
    }

    def void javaFile() {
        File javaFile = new File(projectDir, "src/main/java/netflix/Hello.java")
        Files.createParentDirs(javaFile)
        javaFile.text = '''
package netflix;

public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
'''
    }

    def void checkFile() {
        File checkFile = new File(projectDir, "config/checkstyle/checkstyle.xml")
        Files.createParentDirs(checkFile)
        checkFile.text = '''
<!DOCTYPE module PUBLIC
    "-//Puppy Crawl//DTD Check Configuration 1.2//EN"
    "http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="ConstantName"/>
    </module>
</module>
'''
    }

    def void testFile() {
        File testFile = new File(projectDir, "src/test/java/netflix/HelloTest.java")
        Files.createParentDirs(testFile)
        testFile.text = '''
package netflix;

import org.junit.Test;

public class HelloTest {
    @Test
    public void testSomething() {
        System.out.println("Nothing to test");
    }
}
'''
    }
}
"""
    }

    @Issue("gradle/core-issues#89")
    @NotYetImplemented
    def "build passes"() {
        expect:
        succeeds("test")
    }
}
