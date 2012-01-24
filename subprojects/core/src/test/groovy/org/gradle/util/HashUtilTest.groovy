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
package org.gradle.util

import spock.lang.Specification

class HashUtilTest extends Specification {
    def "creates Hex SHA1 for string input"() {
        expect:
        HashUtil.createHashString("", "SHA1") == "da39a3ee5e6b4b0d3255bfef95601890afd80709"
        HashUtil.createHashString("a", "SHA1") == "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8"
        HashUtil.createHashString("i", "SHA1") == "042dc4512fa3d391c5170cf3aa61e6a638f84342"
    }

    def "creates short MD5 for string input"() {
        expect:
        HashUtil.createCompactMD5("") == "6k3m6dj3o0m82ej009j3mfggju"
        HashUtil.createCompactMD5("a") == "co5qrjg7hmqk33gsps9kne9j1"
        HashUtil.createCompactMD5("i") == "46bg60milgs1hubil371u1l1q1"
    }
}
