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

import org.bouncycastle.bcpg.ArmoredInputStream
import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.signatory.SignatoryProvider

class SigningTasksSpec extends SigningProjectSpec {
    
    def setup() {
        applyPlugin()
    }
        
    def "sign jar with defaults"() {
        given:
        useJavadocAndSourceJars()
        
        when:
        signing {
            sign jar
            sign sourcesJar, javadocJar
        }
        
        then:
        def signingTasks = [signJar, signSourcesJar, signJavadocJar]
        
        and:
        jar in signJar.dependsOn
        sourcesJar in signSourcesJar.dependsOn
        javadocJar in signJavadocJar.dependsOn
        
        and:
        signingTasks.every { it.singleSignature in configurations.signatures.artifacts }

        and:
        signingTasks.every { it.signatory == signing.signatory }
    }
    
    def "sign method return values"() {
        given:
        useJavadocAndSourceJars()
        
        when:
        def signJarTask = signing.sign(jar).first()
        
        then:
        signJarTask.name == "signJar"
        
        when:
        def (signSourcesJarTask, signJavadocJarTask) = signing.sign(sourcesJar, javadocJar)
        
        then:
        [signSourcesJarTask, signJavadocJarTask]*.name == ["signSourcesJar", "signJavadocJar"]
    }

    def "signing supports custom Signatory"() {
        setup:
        def input = getResourceFile("1.txt")
        def signatureBytes = "Bogus signature".getBytes("UTF-8")
        signing.signatories = Mock(SignatoryProvider) {
            getDefaultSignatory(_) >> Mock(Signatory) {
                sign(_, _) >> { toSign, destination ->
                    destination.write(signatureBytes)
                }
            }
        }

        def signTask = task("signTestInput", type: Sign) {
            sign input
        }

        when:
        signTask.execute()

        then:
        new ArmoredInputStream(new FileInputStream(signTask.signatureFiles.singleFile)).bytes == signatureBytes
    }

}
