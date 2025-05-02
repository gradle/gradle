/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.credentials

import org.gradle.util.TestCredentialUtil
import spock.lang.Specification

class CredentialsToStringSpec extends Specification {

    private static String secretValue = UUID.randomUUID().toString()

    def "sensitive credentials values are not leaked via toString"() {
        expect:
        !credentials.toString().contains(secretValue)

        where:
        credentials << [makeAwsCredentials(secretValue), makePasswordCredentials(secretValue), makeHttpHeaderCredentials(secretValue)]
    }

    private static DefaultAwsCredentials makeAwsCredentials(String secretValue) {
        return TestCredentialUtil.defaultAwsCredentials(secretValue)
    }

    private static DefaultPasswordCredentials makePasswordCredentials(String secretValue) {
        return TestCredentialUtil.defaultPasswordCredentials(UUID.randomUUID().toString(), secretValue)
    }

    private static DefaultHttpHeaderCredentials makeHttpHeaderCredentials(String secretValue) {
        return TestCredentialUtil.defaultHttpHeaderCredentials(UUID.randomUUID().toString(), secretValue)
    }
}
