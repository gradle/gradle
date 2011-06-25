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
        signing.signatories instanceof Map
    }
    
    def "signing configuration"() {
        expect:
        signing != null
        signing instanceof SigningSettings
        signing.project == project
    }
    
    def "default signatory with no properties"() {
        expect:
        signing.signatory == null
    }
    
    def "default type"() {
        expect:
        signing.type.extension == "asc"
    }
    

}