/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.hash

import org.gradle.api.UncheckedIOException
import spock.lang.Issue
import spock.lang.Specification

class HashUtilTest extends Specification {
    String stringToHash = "a test string"
    String md5HashString = "b1a4cf30d3f4095f0a7d2a6676bcae77"
    String sha1HashString = "2da75da5c85478df42df0f917700241ed282f599"
    String sha256HashString = "b830543dc5d1466110538736d35c37cc61d32076a69de65c42913dfbb1961f46"
    String sha512HashString = "fd308aadbf52384412c4ba3e2dfe3551e0faa2e7455898dae04fda4f238569e3889c56cbd4d120cf69f81a5f06456f327c19100eaed2e590888342f1ce3e0261"

    def "createHash from String returns MD5 hash" () {
        expect:
        HashUtil.createHash(stringToHash, "MD5").asHexString() == md5HashString
    }

    def "createHash from File returns MD5 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.createHash(file, "MD5").asHexString() == md5HashString

        cleanup:
        file?.delete()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "createHash from File adds filename to UncheckedIOException" () {
        setup:
        String filename = "/some/path/to/file"
        File file = Spy(File, constructorArgs: [ filename ]) {
            1 * getPath() >> { throw new UncheckedIOException(new IOException("thrown from spy class")) }
            1 * getAbsolutePath() >> filename
        }

        when:
        HashUtil.createHash(file, "MD5")

        then:
        UncheckedIOException e = thrown()
        e.message.contains(filename)
        e.message.contains("MD5")
    }

    def "createHash from InputStream returns MD5 hash" () {
        expect:
        HashUtil.createHash(new ByteArrayInputStream(stringToHash.bytes), "MD5").asHexString() == md5HashString
    }

    def "createHash from InputStream wraps IOException in UncheckedIOException" () {
        setup:
        IOException ioe = new IOException("thrown from stub class")
        InputStream stubInputStream = Stub(InputStream) {
            _ * read(_ as byte[]) >> { throw ioe }
        }

        when:
        HashUtil.createHash(stubInputStream, "MD5")

        then:
        UncheckedIOException e = thrown()
        e.cause == ioe
    }

    def "createCompactMD5 returns correct String" () {
        expect:
        HashUtil.createCompactMD5(stringToHash) == new BigInteger(md5HashString, 16).toString(36)
    }

    def "sha1 from byteArray returns SHA1 hash" () {
        expect:
        HashUtil.sha1(stringToHash.bytes).asHexString() == sha1HashString
    }

    def "sha1 from InputStream returns SHA1 hash" () {
        expect:
        HashUtil.sha1(new ByteArrayInputStream(stringToHash.bytes)).asHexString() == sha1HashString
    }

    def "sha1 from File returns SHA1 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.sha1(file).asHexString() == sha1HashString

        cleanup:
        file?.delete()
    }

    def "sha256 from byteArray returns SHA-256 hash" () {
        expect:
        HashUtil.sha256(stringToHash.bytes).asHexString() == sha256HashString
    }

    def "sha256 from InputStream returns SHA-256 hash" () {
        expect:
        HashUtil.sha256(new ByteArrayInputStream(stringToHash.bytes)).asHexString() == sha256HashString
    }

    def "sha256 from File returns SHA-256 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.sha256(file).asHexString() == sha256HashString

        cleanup:
        file?.delete()
    }

    def "sha512 from InputStream returns SHA-512 hash" () {
        expect:
        HashUtil.sha512(new ByteArrayInputStream(stringToHash.bytes)).asHexString() == sha512HashString
    }

    def "sha512 from File returns SHA-512 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.sha512(file).asHexString() == sha512HashString

        cleanup:
        file?.delete()
    }
}
