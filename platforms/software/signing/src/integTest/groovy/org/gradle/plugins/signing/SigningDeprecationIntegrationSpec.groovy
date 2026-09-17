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

    def "Signature.setName and getName are deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            def sig = signTask.getSignatures().first()
            sig.setName("custom-name")
            sig.getName()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.setName(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        executer.expectDocumentedDeprecationWarning("The Signature.getName() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.setExtension and getExtension are deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            def sig = signTask.getSignatures().first()
            sig.setExtension("sig")
            sig.getExtension()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.setExtension(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        executer.expectDocumentedDeprecationWarning("The Signature.getExtension() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.setType and getType are deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            def sig = signTask.getSignatures().first()
            sig.setType("pgp")
            sig.getType()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.setType(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        executer.expectDocumentedDeprecationWarning("The Signature.getType() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.setClassifier and getClassifier are deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            def sig = signTask.getSignatures().first()
            sig.setClassifier("sources")
            sig.getClassifier()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.setClassifier(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        executer.expectDocumentedDeprecationWarning("The Signature.getClassifier() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.setDate and getDate are deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            def sig = signTask.getSignatures().first()
            sig.setDate(new Date())
            sig.getDate()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.setDate(Date) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        executer.expectDocumentedDeprecationWarning("The Signature.getDate() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.getBuildDependencies is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            signTask.getSignatures().first().getBuildDependencies()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.getBuildDependencies() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.builtBy is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            signTask.getSignatures().first().builtBy("help")
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.builtBy(Object...) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "Signature.shouldBePublished is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.create("sign", Sign) {
                sign(file("file.txt"))
            }
            signTask.getSignatures().first().shouldBePublished()
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The Signature.shouldBePublished() method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_as_artifact")
        succeeds("help")
    }

    def "constructing a Signature with a file is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.register("sign", Sign).get()
            new Signature(file("file.txt"), signTask)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Constructing a Signature object has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_constructors")
        succeeds("help")
    }

    def "constructing a Signature with a file and classifier is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.register("sign", Sign).get()
            new Signature(file("file.txt"), "sources", signTask)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Constructing a Signature object has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_constructors")
        succeeds("help")
    }

    def "constructing a Signature with closures is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.register("sign", Sign).get()
            new Signature({ file("file.txt") }, { "sources" }, signTask)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Constructing a Signature object has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_constructors")
        succeeds("help")
    }

    def "constructing a Signature with callables is deprecated"() {
        given:
        buildFile << """
            def signTask = tasks.register("sign", Sign).get()
            java.util.concurrent.Callable<File> toSign = { file("file.txt") }
            java.util.concurrent.Callable<String> classifier = { "sources" }
            new Signature(toSign, classifier, signTask)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Constructing a Signature object has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_constructors")
        succeeds("help")
    }

    def "constructing a Signature with a publish artifact is deprecated"() {
        given:
        buildFile << """
            configurations {
                someConfig {
                    outgoing {
                        artifact(file("file.txt"))
                    }
                }
            }
            def artifact = configurations.someConfig.artifacts.first()
            def signTask = tasks.register("sign", Sign).get()
            new Signature(artifact, signTask)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("Constructing a Signature object has been deprecated. This will fail with an error in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_signature_constructors")
        succeeds("help")
    }

    def "SigningExtension.sign with classifier is deprecated"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            signing {
                ${signingConfiguration()}
            }
            def testFile = file("file.txt")
            testFile.text = "content"
            signing.sign("ignored-classifier", testFile)
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The SigningExtension.sign(String, File...) method has been deprecated. This is scheduled to be removed in Gradle 10. Use sign(File...) instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate_sign_classifier")
        succeeds("help")
    }

}
