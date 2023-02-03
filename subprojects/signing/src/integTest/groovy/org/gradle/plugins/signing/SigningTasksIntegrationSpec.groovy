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


import org.gradle.plugins.signing.signatory.internal.gnupg.GnupgSignatoryProvider
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires

import static org.gradle.plugins.signing.SigningIntegrationSpec.SignMethod.GPG_CMD
import static org.gradle.plugins.signing.SigningIntegrationSpec.SignMethod.OPEN_GPG

class SigningTasksIntegrationSpec extends SigningIntegrationSpec {

    def "sign jar with default signatory"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}

            signing {
                ${signingConfiguration()}
                sign jar
            }
        """

        when:
        run "signJar"

        then:
        executedAndNotSkipped(":signJar")

        and:
        file("build", "libs", "sign-1.0.jar.asc").text

        when:
        run "signJar"

        then:
        skipped(":signJar")
    }

    def "sign multiple jars with default signatory"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            ${javadocAndSourceJarsScript}

            signing {
                ${signingConfiguration()}
                sign jar, javadocJar, sourcesJar
            }
        """

        when:
        run "signJar", "signJavadocJar", "signSourcesJar"

        then:
        executedAndNotSkipped(":signJar", ":signJavadocJar", ":signSourcesJar")

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "libs", "sign-1.0-javadoc.jar.asc").text
        file("build", "libs", "sign-1.0-sources.jar.asc").text

        when:
        run "signJar", "signJavadocJar", "signSourcesJar"

        then:
        skipped(":signJar", ":signJavadocJar", ":signSourcesJar")
    }

    @Requires(adhoc = { GpgCmdFixture.getAvailableGpg() != null })
    def "out-of-date when signatory changes"() {
        given:
        def originalSignMethod = signMethod
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            signing {
                ${signingConfiguration()}
                sign(jar)
            }
        """

        when:
        run "signJar"

        then:
        executedAndNotSkipped(":signJar")

        when:
        def newSignMethod = originalSignMethod == GPG_CMD ? OPEN_GPG : GPG_CMD
        if (newSignMethod == GPG_CMD) {
            setupGpgCmd()
        }
        def signatoryProviderClass = newSignMethod == GPG_CMD ? GnupgSignatoryProvider : PgpSignatoryProvider
        buildFile << """
            signing {
                signatories = new ${signatoryProviderClass.name}()
            }
        """
        run "signJar", "-i"

        then:
        executedAndNotSkipped(":signJar")

        when:
        run "signJar"

        then:
        skipped(":signJar")
    }

    def "out-of-date when signatureType changes"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            signing {
                ${signingConfiguration()}
                sign(jar)
            }
        """

        when:
        run "signJar"

        then:
        executedAndNotSkipped(":signJar")

        when:
        buildFile << """
            signing {
                signatureTypes.defaultType = 'sig'
            }
        """
        run "signJar"

        then:
        executedAndNotSkipped(":signJar")

        when:
        run "signJar"

        then:
        skipped(":signJar")
    }

    def "out-of-date when input file changes"() {
        given:
        def inputFile = file("input.txt")
        inputFile.text = "foo"
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            signing {
                ${signingConfiguration()}
            }
            task signCustomFile(type: Sign) {
                sign(file("input.txt"))
            }
        """

        when:
        run "signCustomFile"

        then:
        executedAndNotSkipped(":signCustomFile")
        file("input.txt.asc").exists()

        when:
        inputFile.text = "bar"
        run "signCustomFile"

        then:
        executedAndNotSkipped(":signCustomFile")

        when:
        run "signCustomFile"

        then:
        skipped(":signCustomFile")
    }

    def "out-of-date when output file is deleted"() {
        given:
        file("input.txt") << "foo"
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            signing {
                ${signingConfiguration()}
            }
            task signCustomFile(type: Sign) {
                sign(file("input.txt"))
            }
        """

        when:
        run "signCustomFile"

        then:
        executedAndNotSkipped(":signCustomFile")
        def outputFile = file("input.txt.asc")
        outputFile.exists()

        when:
        outputFile.delete()
        run "signCustomFile"

        then:
        executedAndNotSkipped(":signCustomFile")

        when:
        run "signCustomFile"

        then:
        skipped(":signCustomFile")
    }

    def "up-to-date when order of signed files changes"() {
        given:
        def inputFile1 = file("input1.txt") << "foo"
        def inputFile2 = file("input2.txt") << "bar"
        def writeBuildFile = { TestFile... inputFiles ->
            buildFile.text = """
            apply plugin: 'signing'
            ${keyInfo.addAsPropertiesScript()}
            signing {
                ${signingConfiguration()}
            }
            task signCustomFiles(type: Sign) {
                sign(${inputFiles.collect { "file('${it.name}')" }.join(', ')})
            }
        """
        }

        when:
        writeBuildFile(inputFile1, inputFile2)
        run "signCustomFiles"

        then:
        executedAndNotSkipped(":signCustomFiles")
        file("input1.txt.asc").exists()
        file("input2.txt.asc").exists()

        when:
        writeBuildFile(inputFile2, inputFile1)
        run "signCustomFiles"

        then:
        skipped(":signCustomFiles")
    }

    def "trying to sign a task that isn't an archive task gives nice enough message"() {
        given:
        buildFile << """
            signing {
                ${signingConfiguration()}
                sign clean
            }
        """

        when:
        runAndFail "signClean"

        then:
        failureHasCause "You cannot sign tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign task ':clean')"
    }

    def "changes to task information after signing block are respected"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}

            signing {
                ${signingConfiguration()}
                sign jar
            }

            jar {
                archiveBaseName = "changed"
                archiveClassifier = "custom"
            }
        """

        when:
        run "signJar"

        then:
        executedAndNotSkipped(":signJar")

        and:
        file("build", "libs", "changed-1.0-custom.jar.asc").text

    }

    def "sign with subkey"() {
        given:
        buildFile << """
            ${getKeyInfo("subkey").addAsPropertiesScript()}

            signing {
                sign jar
            }
        """

        when:
        run "signJar"

        then:
        executedAndNotSkipped(":signJar")

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
    }
}
