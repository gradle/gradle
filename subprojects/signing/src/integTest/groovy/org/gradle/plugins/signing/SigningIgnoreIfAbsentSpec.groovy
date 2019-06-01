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

class SigningIgnoreIfAbsentSpec extends SigningIntegrationSpec {

    def "signing fails if file does not exists"() {
        given:
        buildFile << """
${keyInfo.addAsPropertiesScript()}
def testFile = file("not_exist.txt")

signing {
    sign testFile
}
"""
        when:
        fails "help"

        then:
        failureHasCause "Unable to generate signature for \'${file('not_exist.txt').absolutePath}\' as the file doesn't exists"
    }

    def "signing not fails for non-existing file if signature is not required"() {
        given:
        buildFile << """
    ${keyInfo.addAsPropertiesScript()}
    def testFile = file("not_exist.txt")
    
    signing {
        required = false
        sign testFile
    }
"""
        expect:
        succeeds "help"
    }

    def "signing not fails for non-existing file if source PublicationArtifact is optional"() {
        given:
        buildFile << """
${keyInfo.addAsPropertiesScript()}

apply plugin: 'maven-publish'

def testFile = file("not_exist.txt")

publishing {
    publications {
        def publication = maven(MavenPublication) {
            artifact(testFile) {
                ignoreIfAbsent = true
            }
        }
        
        task('signPublicationArtifact') {
            doFirst {
                signing.sign(publication)
            }
        }
    }
}
"""
        expect:
        succeeds 'signPublicationArtifact'
    }
}
