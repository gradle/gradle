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


import org.gradle.plugins.signing.signatory.SignatoryProvider
import spock.lang.Unroll

class ConventionSmokeSpec extends SigningProjectSpec {

    def setup() {
        applyPlugin()
    }

    def "signing block"() {
        when:
        signing {
            signatories {

            }
        }

        then:
        notThrown Exception
    }

    def "signatories"() {
        expect:
        signing.signatories != null
        signing.signatories instanceof SignatoryProvider
    }

    def "signing configuration"() {
        expect:
        signing != null
        signing instanceof SigningExtension
        signing.project == project
    }

    def "default signatory with no properties"() {
        expect:
        signing.signatory == null
    }

    def "default type"() {
        expect:
        signing.signatureType.extension == "asc"
    }

    @Unroll
    def "required has flexible input"() {
        when:
        signing.required = value

        then:
        signing.required == required

        where:
        value | required
        true  | true
        false | false
        []    | false
        ""    | false
    }

    def "can supply a callable as the required value"() {
        given:
        def flag = false
        signing.required { flag }

        expect:
        !signing.required

        when:
        flag = true

        then:
        signing.required
    }


}
