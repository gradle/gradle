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

import static org.gradle.internal.hash.Hashing.md5

class HashUtilTest extends Specification {
    String stringToHash = "a test string"
    String md5HashString = "b1a4cf30d3f4095f0a7d2a6676bcae77"
    String sha1HashString = "2da75da5c85478df42df0f917700241ed282f599"
    String sha256HashString = "b830543dc5d1466110538736d35c37cc61d32076a69de65c42913dfbb1961f46"

    def "createHash from String returns MD5 hash" () {
        expect:
        md5().hashString(stringToHash).toString() == md5HashString
    }

    def "createHash from File returns MD5 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.md5(file).toString() == md5HashString

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
        HashUtil.md5(file)

        then:
        UncheckedIOException e = thrown()
        e.message.contains(filename)
        e.message.contains("MD5")
    }

    def "createHash from InputStream returns MD5 hash" () {
        expect:
        HashUtil.md5(new ByteArrayInputStream(stringToHash.bytes)).toString() == md5HashString
    }

    def "createHash from InputStream wraps IOException in UncheckedIOException" () {
        setup:
        IOException ioe = new IOException("thrown from stub class")
        InputStream stubInputStream = Stub(InputStream) {
            _ * read(_ as byte[]) >> { throw ioe }
        }

        when:
        HashUtil.md5(stubInputStream)

        then:
        UncheckedIOException e = thrown()
        e.cause == ioe
    }

    def "createCompactMD5 returns correct String" () {
        expect:
        HashUtil.createCompactMD5(stringToHash) == new BigInteger(md5HashString, 16).toString(36)
        HashUtil.createCompactMD5("") == "ck2u8j60r58fu0sgyxrigm3cu"
        HashUtil.createCompactMD5("a") == "r6p51cluyxfm1x21kf967yw1"
        HashUtil.createCompactMD5("i") == "7ycx034q3zbhupl01mv32dx6p"
    }

    def "sha1 from byteArray returns SHA1 hash" () {
        expect:
        HashUtil.sha1(stringToHash.bytes).toString() == sha1HashString
    }

    def "sha1 from InputStream returns SHA1 hash" () {
        expect:
        HashUtil.sha1(new ByteArrayInputStream(stringToHash.bytes)).toString() == sha1HashString
    }

    def "sha1 from File returns SHA1 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.sha1(file).toString() == sha1HashString

        cleanup:
        file?.delete()
    }

    def "sha256 from byteArray returns SHA-256 hash" () {
        expect:
        HashUtil.sha256(stringToHash.bytes).toString() == sha256HashString
    }

    def "sha256 from InputStream returns SHA-256 hash" () {
        expect:
        HashUtil.sha256(new ByteArrayInputStream(stringToHash.bytes)).toString() == sha256HashString
    }

    def "sha256 from File returns SHA-256 hash" () {
        setup:
        File file = File.createTempFile("HashUtilTest", null)
        file << stringToHash

        expect:
        HashUtil.sha256(file).toString() == sha256HashString

        cleanup:
        file?.delete()
    }
}
