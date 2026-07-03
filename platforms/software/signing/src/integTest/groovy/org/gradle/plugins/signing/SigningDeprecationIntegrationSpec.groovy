/*
 * Copyright 2025 the original author or authors.
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

class SigningDeprecationIntegrationSpec extends SigningIntegrationSpec {

    def "SigningExtension.getConfiguration is deprecated"() {
        given:
        buildFile << """
            signing.getConfiguration()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The SigningExtension.getConfiguration() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "SigningExtension.setConfiguration is deprecated"() {
        given:
        buildFile << """
            def myConfig = configurations.create("mySignatures")
            signing.setConfiguration(myConfig)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The SigningExtension.setConfiguration(Configuration) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "creating the signatures configuration before applying the signing plugin is deprecated"() {
        given:
        buildFile.text = """
            apply plugin: 'java-library'
            configurations.create("signatures")
            apply plugin: 'signing'
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Creating the 'signatures' configuration manually has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

}
