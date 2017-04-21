/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class TestTaskSnapshottingConfigurationIntegrationTest extends AbstractIntegrationSpec {
    TestFile resourceFile

    def setup() {
        buildFile << """     
            allprojects {
                apply plugin: 'java'
                repositories { jcenter() }
            }
        """
        settingsFile << """
            include 'dependency'
        """
        resourceFile = file("dependency/src/main/resources/dependency/foo.properties")
        resourceFile << """
            someProperty = true
        """
    }

    def createTestProject(String name) {
        file(name, 'build.gradle') << """
            dependencies {
                testCompile 'junit:junit:4.12'
                testCompile project(":dependency")
            }
        """.stripIndent()
        settingsFile << """
            include '${name}'
        """
        file("${name}/src/test/java/MyTest.java") << """
            import org.junit.*;

            public class MyTest {
               @Test
               public void test() {
                  Assert.assertTrue(true);
               }
            }
        """.stripIndent()
    }

    def "can ignore files on runtime classpath"() {
        createTestProject('consumer')

        buildFile << """     
            import org.gradle.api.snapshotting.ClasspathEntry
        
            allprojects {
                snapshotting {
                    snapshotter(ClasspathEntry) {
                        exclude 'foo.properties'
                    }
                }
            }
        """

        when:
        succeeds 'test'
        then:
        noExceptionThrown()

        when:
        renameResourceFile()
        succeeds ':consumer:test'
        then:
        nonSkippedTasks.contains(':dependency:jar')
        skippedTasks.contains(':consumer:test')
    }

    def "snapshotting configuration is project specific"() {
        createTestProject('consumer')
        createTestProject('defaultSnapshotting')
        file('consumer/build.gradle') << """
            import org.gradle.api.snapshotting.ClasspathEntry

            snapshotting {
                snapshotter(ClasspathEntry) {
                    exclude 'foo.properties'
                }
            }            
        """.stripIndent()

        when:
        succeeds ':consumer:test', ':defaultSnapshotting:test'

        then:
        executedAndNotSkipped ':consumer:test', ':defaultSnapshotting:test'

        when:
        renameResourceFile()
        succeeds ':consumer:test', ':defaultSnapshotting:test'

        then:
        executedAndNotSkipped ':defaultSnapshotting:test'
        skipped ':consumer:test'
    }

    private void renameResourceFile() {
        resourceFile.moveToDirectory(file("dependency/src/main/resources/dependency/other/"))
    }
}
