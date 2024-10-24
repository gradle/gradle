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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.IvyHttpModule
import spock.lang.Issue

/**
 * We are using Ivy here, but the strategy is the same for any kind of repository.
 */
class CachedDependencyResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    IvyHttpModule module

    TestFile downloaded
    TestFile.Snapshot lastState

    def setup() {
        buildFile << """
repositories {
    ivy { url = "${ivyHttpRepo.uri}" }
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
        module.ivy.expectGet()
        module.jar.expectGet()
        resolve()
    }

    void resolve() {
        if (downloaded.exists()) {
            lastState = downloaded.snapshot()
        }

        succeeds ":retrieve"
    }

    void headOnlyRequests() {
        module.ivy.expectHead()
        module.jar.expectHead()
    }

    void headSha1ThenGetRequests() {
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()

        module.jar.expectHead()
        module.jar.sha1.expectGet()
        module.jar.expectGet()
    }

    void sha1OnlyRequests() {
        module.ivy.sha1.expectGet()
        module.jar.sha1.expectGet()
    }

    void sha1ThenGetRequests() {
        module.ivy.sha1.expectGet()
        module.ivy.expectGet()

        module.jar.sha1.expectGet()
        module.jar.expectGet()
    }

    void headThenSha1Requests() {
        module.ivy.expectHead()
        module.ivy.sha1.expectGet()

        module.jar.expectHead()
        module.jar.sha1.expectGet()
    }

    void headThenGetRequests() {
        module.ivy.expectHead()
        module.ivy.expectGet()

        module.jar.expectHead()
        module.jar.expectGet()
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

    @Issue("GRADLE-2781")
    def "no leading zeros in sha1 checksums supported"() {
        given:
        def sha1 = new File("${module.jarFile.absolutePath}.sha1")
        server.etags = null
        server.sendLastModified = false
        byte[] jarBytes = [0, 0, 0, 5] // this should produce leading zeros
        module.jarFile.bytes = jarBytes
        sha1.text = Hashing.sha1().hashBytes(jarBytes).toZeroPaddedString(Hashing.sha1().hexDigits)
        initialResolve()
        expect:
        headThenSha1Requests()
        trimLeadingZerosFromSHA1(sha1)
        unchangedResolve()
    }

    def trimLeadingZerosFromSHA1(File sha1) {
        //remove leading zeros from sha1 checksum
        sha1.text = sha1.text.replaceAll("^0+", "")
    }
}
