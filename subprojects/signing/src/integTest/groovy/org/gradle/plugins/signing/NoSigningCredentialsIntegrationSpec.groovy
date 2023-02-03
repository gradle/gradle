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
package org.gradle.plugins.signing

class NoSigningCredentialsIntegrationSpec extends SigningIntegrationSpec {

    def setup() {
        using m2
        executer.withArguments("-info")
    }

    def "trying to perform a signing operation without a signatory produces reasonable error"() {
        when:
        buildFile << """
            signing {
                sign jar
            }
        """

        then:
        fails ":signJar"

        and:
        failureHasCause "Cannot perform signing task ':signJar' because it has no configured signatory"
    }
}
