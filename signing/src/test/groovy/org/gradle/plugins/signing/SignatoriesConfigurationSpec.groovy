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

import org.gradle.api.InvalidUserDataException

class SignatoriesConfigurationSpec extends SigningProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "default signatory returns null if no properties set"() {
        expect:
        signing.signatory == null
    }

    def "default signatory with properties"() {
        when:
        addSigningProperties()

        then:
        signing.signatory != null
    }

    def "defining signatories with properties"() {
        given:
        def properties = signingPropertiesSet

        when:
        signing {
            signatories {
                custom properties.keyId, properties.secretKeyRingFile, properties.password
            }
        }

        then:
        signing.signatories.custom != null
        signing.signatories.custom.keyId == properties.keyId
    }

    def "defining signatories with default properties"() {
        given:
        def properties = addSigningProperties(prefix: "custom")

        when:
        signing {
            signatories {
                custom()
            }
        }

        then:
        signing.signatories.custom != null
        signing.signatories.custom.keyId == properties.keyId
    }

    def "trying to read non existent file produces reasonable error message"() {
        when:
        project.ext["signing.keyId"] = "aaaaaaaa"
        project.ext["signing.secretKeyRingFile"] = "i/dont/exist"
        project.ext["signing.password"] = "anything"

        and:
        signing.signatory

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains "it does not exist"
    }

    def "trying to use an invalid key ring file produces a reasonable error message"() {
        given:
        addSigningProperties(set: "invalid-key-ring")

        when:
        signing.signatory

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains "Unable to read secret key from"
    }

}
