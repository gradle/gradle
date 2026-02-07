/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.util.internal

import spock.lang.Specification

class WrapperCredentialsTest extends Specification {
    private static WrapperCredentials testBasicUserInfo(int index) {
        return WrapperCredentials.fromBasicUserInfo("TestUser$index:TestPassword$index")
    }

    private static WrapperCredentials testTokenInfo(int index) {
        return WrapperCredentials.fromToken("TestToken$index")
    }

    private static String sysPropertyKey(String hostname, String key) {
        return hostname != null ? "gradle.$hostname.$key" : "gradle.$key"
    }

    private static Map<String, String> wrapperUserAndPassKeys(int index, String hostname = null) {
        return [
            (sysPropertyKey(hostname, "wrapperUser")): "TestUser$index".toString(),
            (sysPropertyKey(hostname, "wrapperPassword")): "TestPassword$index".toString(),
        ]
    }

    private static Map<String, String> wrapperTokenKeys(int index, String hostname = null) {
        return [(sysPropertyKey(hostname, "wrapperToken")): "TestToken$index".toString()]
    }

    def "test findCredentials"() {
        expect:
        WrapperCredentials.findCredentials(new URI("https://${hostname}/dists/gradle-x.zip")) { sysProperties[it] } == expectation

        where:
        hostname           | sysProperties                                                                          || expectation
        "my.company1.com"  | wrapperUserAndPassKeys(1)                                                              || testBasicUserInfo(1)
        "my.company1.com"  | wrapperTokenKeys(1)                                                                    || testTokenInfo(1)
        "my.company1.com"  | wrapperUserAndPassKeys(1, "my_company1_com")                                           || testBasicUserInfo(1)
        "my.company1.com"  | wrapperTokenKeys(1, "my_company1_com")                                                 || testTokenInfo(1)
        "MY.Company1.com"  | wrapperUserAndPassKeys(1, "my_company1_com")                                           || testBasicUserInfo(1)
        "MY.Company1.com"  | wrapperTokenKeys(1, "my_company1_com")                                                 || testTokenInfo(1)
        "my.company1.com"  | wrapperUserAndPassKeys(1) + wrapperUserAndPassKeys(2, "my_company1_com")               || testBasicUserInfo(2)
        "my.company1.com"  | wrapperTokenKeys(1) + wrapperTokenKeys(2, "my_company1_com")                           || testTokenInfo(2)
        "my.company1.com"  | wrapperUserAndPassKeys(1) + wrapperTokenKeys(2)                                        || testTokenInfo(2)
        "my.company1.com"  | wrapperUserAndPassKeys(1, "my_company1_com") + wrapperTokenKeys(2)                     || testTokenInfo(2)
        "my.company1.com"  | wrapperUserAndPassKeys(1) + wrapperTokenKeys(2, "my_company1_com")                     || testTokenInfo(2)
        "my.company1.com"  | wrapperUserAndPassKeys(1, "my_company1_com") + wrapperTokenKeys(2, "my_company1_com")  || testTokenInfo(2)
        "TestUser1:TestPassword1@my.company1.com"  | [:]                                                            || testBasicUserInfo(1)
        "TestUser1:TestPassword1@my.company1.com"  | wrapperTokenKeys(2)                                            || testTokenInfo(2)
    }
}
