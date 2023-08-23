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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotParallelExecutor)
class JavaAnnotationProcessingCompileAvoidanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "include 'a', 'b'"
        buildFile << '''
            allprojects {
                apply plugin: 'java'
            }
        '''

        // annotation processor path uses @Classpath
        file('a/build.gradle') << '''
            configurations {
                annotationProcessor
            }
            dependencies {
                annotationProcessor project(':b')
            }
            compileJava {
                options.annotationProcessorPath = configurations.annotationProcessor
            }
        '''

        file('a/src/main/java/A.java') << '''
            public class A {
                public void foo() {
                }
            }
        '''
        file('a/src/main/resources/A.properties') << '''
            aprop=avalue
        '''

        file('b/src/main/java/B.java') << '''
            public class B {
                public int truth() { return 0; }
            }
        '''
        file('b/src/main/resources/B.properties') << '''
            bprop=bvalue
        '''
    }

    def "does not rebuild project when upstream project has not changed, only rebuilt"() {
        given:
        succeeds(":a:assemble")

        when:
        // cleaning b and rebuilding will cause b.jar to be different
        succeeds(":b:clean")
        and:
        succeeds(":a:assemble")

        then:
        result.assertTasksNotSkipped(":b:compileJava", ":b:processResources", ":b:classes", ":b:jar")
        result.assertTasksSkipped(":a:compileJava", ":a:processResources", ":a:classes", ":a:jar", ":a:assemble")
    }
}
