/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.ivy.IvyHttpModule
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TestFile

/**
 * We are using Ivy here, but the strategy is the same for any kind of repository.
 */
class CachedDependencyResolutionIntegrationTest extends AbstractDependencyResolutionTest {

    IvyHttpModule module

    TestFile downloaded
    TestFile.Snapshot lastState

    def setup() {
        server.start()
        buildFile << """
repositories {
    ivy { url "${ivyHttpRepo.uri}" }
}

configurations { compile }

configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

dependencies {
    compile group: "group", name: "projectA", version: "1.1", changing: true
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        module = ivyHttpRepo.module("group", "projectA", "1.1").publish()

        downloaded = file('build/projectA-1.1.jar')
    }

    void initialResolve() {
        module.expectIvyGet()
        module.expectJarGet()

        resolve()
    }

    void resolve() {
        if (downloaded.exists()) {
            lastState = downloaded.snapshot()
        }

        succeeds ":retrieve"
    }

    void headOnlyRequests() {
        module.expectIvyHead()
        module.expectJarHead()
    }

    void headSha1ThenGetRequests() {
        module.expectIvyHead()
        module.expectIvySha1Get()
        module.expectIvyGet()

        module.expectJarHead()
        module.expectJarSha1Get()
        module.expectJarGet()
    }

    void sha1OnlyRequests() {
        module.expectIvySha1Get()
        module.expectJarSha1Get()
    }

    void sha1ThenGetRequests() {
        module.expectIvySha1Get()
        module.expectIvyGet()

        module.expectJarSha1Get()
        module.expectJarGet()
    }

    void headThenSha1Requests() {
        module.expectIvyHead()
        module.expectIvySha1Get()

        module.expectJarHead()
        module.expectJarSha1Get()
    }

    void headThenGetRequests() {
        module.expectIvyHead()
        module.expectIvyGet()

        module.expectJarHead()
        module.expectJarGet()
    }

    void unchangedResolve() {
        resolve()
        downloaded.assertHasNotChangedSince(lastState)
    }

    void changedResolve() {
        resolve()
        downloaded.assertHasChangedSince(lastState)
    }

    void change() {
        module.publishWithChangedContent()
    }

    def "etags are used to determine changed"() {
        given:
        server.etags = HttpServer.EtagStrategy.RAW_SHA1_HEX
        server.sendLastModified = false
        initialResolve()

        expect:
        headOnlyRequests()
        unchangedResolve()

        when:
        change()

        then:
        headSha1ThenGetRequests()
        changedResolve()
    }

    def "last modified and content length are used to determine changed"() {
        given:
        server.etags = null
        initialResolve()

        expect:
        headOnlyRequests()
        unchangedResolve()

        when:
        change()

        then:
        headSha1ThenGetRequests()
        changedResolve()
    }

    def "checksum is used when last modified and content length can't be used"() {
        given:
        server.etags = null
        server.sendLastModified = false
        initialResolve()

        expect:
        headThenSha1Requests()
        unchangedResolve()

        when:
        change()

        then:
        headSha1ThenGetRequests()
        executer.withArgument("-d")
        changedResolve()
    }

    def "no need for sha1 request if we get it in the metadata"() {
        given:
        server.sendSha1Header = true
        initialResolve()

        expect:
        headOnlyRequests()
        unchangedResolve()

        when:
        change()

        then:
        headThenGetRequests()
        changedResolve()
    }

    def "no need for sha1 request if we know the etag is sha1"() {
        given:
        server.etags = HttpServer.EtagStrategy.NEXUS_ENCODED_SHA1
        initialResolve()

        expect:
        headOnlyRequests()
        unchangedResolve()

        when:
        change()

        then:
        headThenGetRequests()
        changedResolve()
    }
}
