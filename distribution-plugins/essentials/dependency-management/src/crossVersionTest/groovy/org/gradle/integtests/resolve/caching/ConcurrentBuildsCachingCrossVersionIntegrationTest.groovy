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

import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheMetadata
import org.gradle.integtests.fixtures.IgnoreVersions
import org.gradle.integtests.resolve.artifactreuse.AbstractCacheReuseCrossVersionIntegrationTest
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule

@IgnoreVersions({ it.artifactCacheLayoutVersion == DefaultArtifactCacheMetadata.CACHE_LAYOUT_VERSION })
class ConcurrentBuildsCachingCrossVersionIntegrationTest extends AbstractCacheReuseCrossVersionIntegrationTest {
    @Rule BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()
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
    doLast {
        configurations.a.files
    }
}

task block1 {
    dependsOn tasks.a
    onlyIf { project.hasProperty("enable-block1") }
    doLast {
        ${blockingServer.callFromBuild("block1")}
    }
}

task b {
    dependsOn tasks.block1
    doLast {
        configurations.b.files
    }
}

task block2 {
    dependsOn tasks.b
    onlyIf { project.hasProperty("enable-block2") }
    doLast {
        ${blockingServer.callFromBuild("block2")}
    }
}

task c {
    dependsOn tasks.block2
}
"""
        expect:
        def block1 = blockingServer.expectAndBlock("block1")
        def block2 = blockingServer.expectAndBlock("block2")

        // Build 1 should download module 1 and check whether it can reuse module 2 files
        mod1.pom.expectGet()
        mod1.artifact.expectGet()
        if (previous.version <= GradleVersion.version("1.8")) {
            mod2.pom.expectGet()
            mod2.artifact.expectGet()
        } else {
            mod2.pom.expectHead()
            mod2.pom.sha1.expectGet()
            mod2.artifact.expectHead()
            mod2.artifact.sha1.expectGet()
        }

        // Build 2 should check whether it can reuse module 1 files and download module 2 files
        mod1.pom.expectHead()
        mod1.pom.sha1.expectGet()
        mod1.artifact.expectHead()
        mod1.artifact.sha1.expectGet()
        mod2.pom.expectGet()
        mod2.artifact.expectGet()

        // Start build 1 then wait until it has run task 'a'.
        def previousExecuter = version(previous)
        previousExecuter.withArgument("-Penable-block1")
        previousExecuter.withTasks("c")
        def build1 = previousExecuter.start()
        block1.waitForAllPendingCalls()

        // Start build 2 then wait until it has run both 'a' and 'b'.
        def currentExecuter = version(current)
        currentExecuter.withArgument("-Penable-block2")
        currentExecuter.withTasks("c")
        def build2 = currentExecuter.start()
        block2.waitForAllPendingCalls()

        // Finish up build 1 and 2
        block1.releaseAll() // finish build 1 while build 2 is still running
        build1.waitForFinish()
        block2.releaseAll()
        build2.waitForFinish()
    }
}
