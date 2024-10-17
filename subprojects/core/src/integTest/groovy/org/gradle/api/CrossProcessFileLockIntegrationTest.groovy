/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.BlockingHttpServer

import static org.gradle.test.fixtures.ConcurrentTestUtil.poll

class CrossProcessFileLockIntegrationTest extends AbstractIntegrationSpec {

    @org.junit.Rule
    BlockingHttpServer server = new BlockingHttpServer()

    @Override
    def setup() {
        server.start()
    }

    def "the task history lock can be acquired when the initial owner is busy executing tasks"() {
        given:
        createDirs("a", "b")
        settingsFile "include 'a', 'b'"

        javaFile("a/src/main/java/A.java", "public class A {}")
        javaFile("b/src/main/java/B.java", "public class B {}")

        buildFile("a/build.gradle", """
            plugins {
                id("java")
            }

            compileJava.doFirst {
                println 'waiting for resource...'
                ${server.callFromTaskAction("first")}
                println 'no more waiting!'
            }
        """)

        buildFile("b/build.gradle", """
            plugins {
                id("java")
            }

            compileJava.doFirst { ${server.callFromTaskAction("second")} }
        """)

        expect:
        server.expectConcurrent("first", "second")

        def handle1 = executer.withArguments(':a:build', '-i').start()
        poll(120) {
            assert handle1.standardOutput.contains('waiting for resource...')
        }
        //first build is waiting for resource, so the lock should be releasable now (for example: the task history lock)

        and:
        def handle2 = executer.withArguments('b:build', '-is').start()
        handle2.waitForFinish()
        handle1.waitForFinish()

        and:
        file("a/build/libs/a.jar").assertExists()
        file("b/build/libs/b.jar").assertExists()
    }
}
