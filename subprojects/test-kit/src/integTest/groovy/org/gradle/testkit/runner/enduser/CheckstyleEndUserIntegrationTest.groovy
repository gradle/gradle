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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testkit.runner.fixtures.NoDebug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import spock.lang.Ignore
import spock.lang.IgnoreIf

@NonCrossVersion
@NoDebug
@IgnoreIf({ GradleContextualExecuter.embedded }) // These tests run builds that themselves run a build in a test worker with 'gradleTestKit()' dependency, which needs to pick up Gradle modules from a real distribution
class CheckstyleEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def setup() {
        buildFile << """
            plugins {
                id "org.gradle.java-gradle-plugin"
                id "org.gradle.groovy"
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation('org.spockframework:spock-core:2.0-M4-groovy-3.0') {
                    exclude group: 'org.codehaus.groovy'
                }
            }
        """

        file("src/test/groovy/Test.groovy") << """
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class Test extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'task runs successfully'() {
        setup:
        temporaryFolder.newFile('settings.gradle') << "rootProject.name = 'checkstyle-test'"
        temporaryFolder.newFile('build.gradle') << '''
apply plugin: 'java'
apply plugin: 'checkstyle'

${mavenCentralRepository()}

dependencies {
    testImplementation 'junit:junit:4.13'
}
'''
        javaFile()

        when:
        def result = GradleRunner.create().withProjectDir(temporaryFolder.root).
                withArguments(['check', '-s']).
                withPluginClasspath().
                withDebug(true).
                build()

        then:
        new File(temporaryFolder.root, 'build/reports/checkstyle/main.xml').exists()
    }

    void javaFile() {
        def javaFile = new File(temporaryFolder.root, "src/main/java/pkg/Hello.java")
        javaFile.parentFile.mkdirs()
        javaFile << '''
package pkg;

public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
'''
        def checkstyleConfig = new File(temporaryFolder.root, "config/checkstyle/checkstyle.xml")
        checkstyleConfig.parentFile.mkdirs()
        checkstyleConfig << '''
<!DOCTYPE module PUBLIC
"-//Puppy Crawl//DTD Check Configuration 1.2//EN"
"http://www.puppycrawl.com/dtds/configuration_1_2.dtd">
    <module name="Checker">
        <module name="TreeWalker">
        <module name="TypeName"/>
    </module>
</module>
'''
    }
}
"""
    }

    @Ignore
    def "build passes"() {
        expect:
        succeeds("test")
    }
}
