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
        signing.signatories.custom.keyId.asHex == properties.keyId
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
        signing.signatories.custom.keyId.asHex == properties.keyId
    }
    
}