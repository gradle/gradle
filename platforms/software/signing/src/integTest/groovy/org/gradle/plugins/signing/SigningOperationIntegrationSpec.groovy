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

class SigningOperationIntegrationSpec extends SigningIntegrationSpec {

    def "SignOperation.sign with classifier is deprecated"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            def dummyFile = file("dummy.txt")
            dummyFile.text = "content"
            signing {
                ${signingConfiguration()}
                sign {
                    sign("ignored-classifier", dummyFile)
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("The SignOperation.sign(String, File...) method has been deprecated. This is scheduled to be removed in Gradle 10. Use sign(File...) instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_sign_classifier")
        run "help"

        then:
        noExceptionThrown()
    }

    def "SignOperation.getSignatures is deprecated"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            def dummyFile = file("dummy.txt")
            dummyFile.text = "content"
            signing {
                ${signingConfiguration()}
                sign {
                    sign(dummyFile)
                    getSignatures()
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("The SignOperation.getSignatures() method has been deprecated. This is scheduled to be removed in Gradle 10. Use getFilesToSign() or getSignatureFiles() instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_sign_operation_signatures")
        run "help"

        then:
        noExceptionThrown()
    }

    def "SignOperation.getSingleSignature is deprecated"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            def dummyFile = file("dummy.txt")
            dummyFile.text = "content"
            signing {
                ${signingConfiguration()}
                sign {
                    sign(dummyFile)
                    getSingleSignature()
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("The SignOperation.getSingleSignature() method has been deprecated. This is scheduled to be removed in Gradle 10. Use getFilesToSign() or getSignatureFiles() instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_sign_operation_signatures")
        run "help"

        then:
        noExceptionThrown()
    }

    def "direct creation of SignOperation fails"() {
        buildFile << """
            new SignOperation()
        """

        when:
        enableProblemsApiCheck()
        fails()

        then:
        failure.assertHasErrorOutput("You cannot create an instance from the abstract class 'org.gradle.plugins.signing.SignOperation'.")

        and:
        verifyAll(receivedProblem) {
            fqid == 'compilation:groovy-dsl:compilation-failed'
            contextualLabel == "Could not compile build file '${buildFile.absolutePath}'."
        }
    }
}
