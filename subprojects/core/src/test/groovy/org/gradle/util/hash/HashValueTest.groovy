/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util.hash

import spock.lang.Specification

class HashValueTest extends Specification {
    def "parses hash value from input strings"() {
        expect:
        def digest = HashValue.parse(inputString)
        digest.asHexString() == hexString

        where:
        hexString                                  | inputString
        "1234"                                     | "1234"
        "abc123"                                   | "ABC123"
        "1"                                        | "000000000000001"
        "123456"                                   | "md5 = 123456"
        "123456"                                   | "sha1 = 123456"
        "76be4c7459d7fb64bf638bac7accd9b6df728f2b" | "SHA1 (dummy.gz) = 76be4c7459d7fb64bf638bac7accd9b6df728f2b"
        "687cab044c8f937b8957166272f1da3c"         | "fontbox-0.8.0-incubating.jar: 68 7C AB 04 4C 8F 93 7B  89 57 16 62 72 F1 DA 3C" // http://repo2.maven.org/maven2/org/apache/pdfbox/fontbox/0.8.0-incubator/fontbox-0.8.0-incubator.jar.md5
        "f951934aa5ae5a88d7e6dfaa6d32307d834a88be" | "f951934aa5ae5a88d7e6dfaa6d32307d834a88be  /home/maven/repository-staging/to-ibiblio/maven2/commons-collections/commons-collections/3.2/commons-collections-3.2.jar"
    }

    def "creates compact string representation"() {
        expect:
        new HashValue(hexString).asCompactString() == compactString

        where:
        hexString                          | compactString
        "1234"                             | "4hk"
        "abc123"                           | "ang93"
        "d41d8cd98f00b204e9800998ecf8427e" | "6k3m6dj3o0m82ej009j3mfggju"
        "FFF"                              | "3vv"
    }

    def "can roundtrip compact sha1 representation"() {
        given:
        def hash = new HashValue("1234")
        
        expect:
        hash.equals(new HashValue(hash.asHexString()))
    }
    
    def "creates short MD5 for string input"() {
        expect:
        HashUtil.createCompactMD5("") == "6k3m6dj3o0m82ej009j3mfggju"
        HashUtil.createCompactMD5("a") == "co5qrjg7hmqk33gsps9kne9j1"
        HashUtil.createCompactMD5("i") == "46bg60milgs1hubil371u1l1q1"
    }
}
