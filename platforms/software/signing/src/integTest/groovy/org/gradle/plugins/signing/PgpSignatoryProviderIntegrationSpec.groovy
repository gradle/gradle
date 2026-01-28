/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.plugins.signing.signatory.pgp.PgpSignatory
import spock.lang.Issue

class PgpSignatoryProviderIntegrationSpec extends SigningIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/35951")
    def "default PgpSignatory private key is cached"() {
        given:
        buildFile """
        task showSignatory() {
            ${keyInfo.addAsPropertiesScript()}
            def signing = project.extensions.getByType(SigningExtension)
            def first = signing.signatory
            ${keyInfo.addAsPropertiesScript()}
            def second = signing.signatory
            println("first === second: " + (first.privateKey === second.privateKey))
            println("first: " + first)
        }
        """

        when:
        succeeds("showSignatory")

        then:
        outputContains("first: ${PgpSignatory.name}@")
        outputContains("first === second: true")
    }

    @Issue("https://github.com/gradle/gradle/issues/35951")
    def "PgpSignatory private key caching is not affected by custom signatories"() {
        given:
        buildFile """
        task showSignatory() {
            ${keyInfo.addAsPropertiesScript()}
            def signing = project.extensions.getByType(SigningExtension)
            def first = signing.signatory
            ${getKeyInfo("subkey").addAsPropertiesScript("custom")}
            signing.signatories {
                custom()
            }
            def custom = signing.signatories.getSignatory("custom")
            assert custom.privateKey != first.privateKey
            println("custom: " + custom)
            def second = signing.signatory
            println("first === second: " + (first.privateKey === second.privateKey))
            println("first: " + first)
        }
        """

        when:
        succeeds("showSignatory")

        then:
        outputContains("first: ${PgpSignatory.name}@")
        outputContains("custom: ${PgpSignatory.name}@")
        outputContains("first === second: true")
    }

    @Issue("https://github.com/gradle/gradle/issues/35951")
    def "default PgpSignatory private key is updateable"() {
        given:
        buildFile """
        task showSignatory() {
            ${keyInfo.addAsPropertiesScript()}
            def signing = project.extensions.getByType(SigningExtension)
            def first = signing.signatory
            ${getKeyInfo("subkey").addAsPropertiesScript()}
            def second = signing.signatory
            println("first === second: " + (first.privateKey === second.privateKey))
        }
        """

        when:
        succeeds("showSignatory")

        then:
        outputContains("first === second: false")
    }
}
