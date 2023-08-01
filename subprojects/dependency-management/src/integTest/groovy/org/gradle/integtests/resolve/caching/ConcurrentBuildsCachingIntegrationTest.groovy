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

package org.gradle.integtests.resolve.caching

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class ConcurrentBuildsCachingIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule BlockingHttpServer blockingServer = new BlockingHttpServer()

    @Override
    def setupBuildOperationFixture() {
        //disable because of a test that is incompatible with the build operation fixture
    }

    def setup() {
        blockingServer.start()
        server.start()
    }

    def "can interleave resolution across multiple build processes"() {
        def mod1 = mavenHttpRepo.module("group1", "module1", "1.0").publish()
        def mod2 = mavenHttpRepo.module("group1", "module2", "0.99").dependsOn(mod1).publish()

        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}
configurations {
    a
    b
}
dependencies {
    a "group1:module1:1.0"
    b "group1:module2:0.99"
}
task a {
    def files = configurations.a
    doLast {
        files.files
    }
}
task b {
    def files = configurations.b
    doLast {
        files.files
    }
}
task block1 {
    doLast {
        ${blockingServer.callFromBuild("block1")}
    }
}
block1.mustRunAfter a
b.mustRunAfter block1

task block2 {
    doLast {
        ${blockingServer.callFromBuild("block2")}
    }
}
block2.mustRunAfter b
"""
        // Ensure scripts are compiled
        run("help")

        expect:
        def block1 = blockingServer.expectAndBlock("block1")
        def block2 = blockingServer.expectAndBlock("block2")

        // Build 1 should download module 1 and reuse cached module 2 state
        mod1.pom.expectGet()
        mod1.artifact.expectGet()

        // Build 2 should download module 2 and reuse cached module 1 state
        mod2.pom.expectGet()
        mod2.artifact.expectGet()

        // Start build 1 then wait until it has run task 'a'.
        executer.withTasks("a", "block1", "b")
        executer.withArgument("--info")
        def build1 = executer.start()
        block1.waitForAllPendingCalls()

        // Start build 2 then wait until it has run both 'a' and 'b'.
        executer.withTasks("a", "b", "block2")
        executer.withArgument("--info")
        def build2 = executer.start()
        block2.waitForAllPendingCalls()

        // Finish up build 1 and 2
        block1.releaseAll() // finish build 1 while build 2 is still running
        build1.waitForFinish()
        block2.releaseAll()
        build2.waitForFinish()
    }
}
