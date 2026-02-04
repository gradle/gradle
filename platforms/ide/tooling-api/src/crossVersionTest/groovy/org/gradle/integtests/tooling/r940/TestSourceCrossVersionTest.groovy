/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r940

import org.gradle.integtests.tooling.TestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.source.ClassSource
import org.gradle.tooling.events.test.source.MethodSource
import org.gradle.tooling.events.test.source.NoSource

@ToolingApiVersion(">=9.4.0")
class TestSourceCrossVersionTest extends ToolingApiSpecification implements TestEventsFixture {

    ProgressEvents events = ProgressEvents.create()

    @TargetGradleVersion(">=9.4.0")
    def "class-based tests have valid test sources"() {
        setup:
        buildFile << """
           plugins {
                 id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:5.7.1'
                runtimeOnly 'org.junit.platform:junit-platform-launcher'
            }

            test {
                useJUnitPlatform()
            }
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.jupiter.api.Test public void foo() throws Exception {
                     org.junit.jupiter.api.Assertions.assertEquals(1, 1);
                }
            }
        """


        when:
        withConnection {
            it.newBuild()
            .forTasks(":test")
            .addProgressListener(events, OperationType.TEST)
            .run()
        }

        then:
        def methodOperation = events.operation('Test foo()(example.MyTest)')
        def classOperation = events.operation('Test class example.MyTest')
        def executorOperation = classOperation.parent
        def testRunOperation = executorOperation.parent

        JvmTestOperationDescriptor methodDescriptor = methodOperation.descriptor
        JvmTestOperationDescriptor classDescriptor = classOperation.descriptor
        JvmTestOperationDescriptor executorDescriptor = executorOperation.descriptor
        JvmTestOperationDescriptor testRunDescriptor = testRunOperation.descriptor

        methodDescriptor.source instanceof MethodSource
        (methodDescriptor.source as MethodSource).className == 'example.MyTest'
        (methodDescriptor.source as MethodSource).methodName == 'foo'
        classDescriptor.source instanceof ClassSource
        (classDescriptor.source as ClassSource).className == 'example.MyTest'
        executorDescriptor.source instanceof NoSource
        testRunDescriptor.source instanceof NoSource
    }

    @TargetGradleVersion(">=9.3 <9.4.0") // different Gradle versions have slightly different test event descriptors; we only want to assert the test source inference
    def "infers test source for older Gradle versions"() {
        setup:
        buildFile << """
           plugins {
                 id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.junit.jupiter:junit-jupiter:5.7.1'
                runtimeOnly 'org.junit.platform:junit-platform-launcher'
            }

            test {
                useJUnitPlatform()
            }
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.jupiter.api.Test public void foo() throws Exception {
                     org.junit.jupiter.api.Assertions.assertEquals(1, 1);
                }
            }
        """

        when:
        withConnection {
            it.newBuild()
                .forTasks(":test")
                .addArguments("--info")
                .addProgressListener(events, OperationType.TASK, OperationType.TEST)
                .run()
        }

        then:
        def methodOperation = events.operation('Test foo()(example.MyTest)')
        def classOperation = events.operation('Test class example.MyTest')
        def executorOperation = classOperation.parent
        def testRunOperation = executorOperation.parent

        JvmTestOperationDescriptor methodDescriptor = methodOperation.descriptor
        JvmTestOperationDescriptor classDescriptor = classOperation.descriptor
        JvmTestOperationDescriptor executorDescriptor = executorOperation.descriptor
        JvmTestOperationDescriptor testRunDescriptor = testRunOperation.descriptor

        methodDescriptor.source instanceof MethodSource
        (methodDescriptor.source as MethodSource).className == 'example.MyTest'
        (methodDescriptor.source as MethodSource).methodName == 'foo()'
        classDescriptor.source instanceof ClassSource
        (classDescriptor.source as ClassSource).className == 'example.MyTest'
        executorDescriptor.source instanceof NoSource
        testRunDescriptor.source instanceof NoSource
    }
}
