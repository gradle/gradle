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

class SigningTasksIntegrationSpec extends SigningIntegrationSpec {

    def setup() {
        buildScript """
            apply plugin: 'java'
            apply plugin: 'signing'
            archivesBaseName = 'sign'
        """
    }
    
    def "sign jar with default signatory"() {
        given:
        buildScript += """
            ${keyInfo.addAsPropertiesScript()}
            
            signing {
                sign jar
            }
        """
        
        when:
        run "signJar"
        
        then:
        ":signJar" in nonSkippedTasks
        
        and:
        file("build", "libs", "sign.jar.asc").text
        
        when:
        run "signJar"
        
        then:
        ":signJar" in skippedTasks
    }
    
    def "sign multiple jars with default signatory"() {
        given:
        buildScript += """
            ${keyInfo.addAsPropertiesScript()}
            ${javadocAndSourceJarsScript}
            
            signing {
                sign jar, javadocJar, sourcesJar
            }
        """
        
        when:
        run "signJar", "signJavadocJar", "signSourcesJar"
        
        then:
        [":signJar", ":signJavadocJar", ":signSourcesJar"].every { it in nonSkippedTasks }
        
        and:
        file("build", "libs", "sign.jar.asc").text
        file("build", "libs", "sign-javadoc.jar.asc").text
        file("build", "libs", "sign-sources.jar.asc").text
        
        when:
        run "signJar", "signJavadocJar", "signSourcesJar"
        
        then:
        [":signJar", ":signJavadocJar", ":signSourcesJar"].every { it in skippedTasks }
    }
    
}