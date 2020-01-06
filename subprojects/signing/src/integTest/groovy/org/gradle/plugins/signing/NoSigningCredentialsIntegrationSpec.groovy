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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Issue
import spock.lang.Unroll

class NoSigningCredentialsIntegrationSpec extends SigningIntegrationSpec {

    def setup() {
        using m2
        executer.withArguments("-info")
    }

    @ToBeFixedForInstantExecution
    def "trying to perform a signing operation without a signatory produces reasonable error"() {
        when:
        buildFile << """
            signing {
                sign jar
            }
        """ << uploadArchives()

        then:
        executer.expectDeprecationWarning()
        fails ":uploadArchives"

        and:
        failureHasCause "Cannot perform signing task ':signJar' because it has no configured signatory"
    }

    @ToBeFixedForInstantExecution
    def "trying to perform a signing operation without a signatory when not required does not error, and other artifacts still uploaded"() {
        when:
        buildFile << """
            signing {
                sign configurations.archives
                required = { false }
            }
        """ << uploadArchives() << signDeploymentPom()

        then:
        executer.expectDeprecationWarnings(2)
        succeeds ":uploadArchives"

        and:
        skipped(":signArchives")

        and:
        jarUploaded()
        signatureNotUploaded()
        pom().exists()
        !pomSignature().exists()

        when:
        buildFile << keyInfo.addAsPropertiesScript()

        then:
        executer.expectDeprecationWarnings(2)
        succeeds ":uploadArchives"

        and:
        executedAndNotSkipped(":signArchives")

        and:
        jarUploaded()
        signatureUploaded()
        pom().exists()
        pomSignature().exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/2267")
    @Unroll
    @ToBeFixedForInstantExecution
    def "trying to perform a signing operation for null signing properties when not required does not error"() {
        when:
        buildFile << """
            signing {
                sign configurations.archives
                required = { false }
            }
        """ << uploadArchives() << signDeploymentPom()
        buildFile << keyInfo.addAsPropertiesScript()
        buildFile << """
            project.ext.setProperty('$signingProperty', null)
        """

        then:
        executer.expectDeprecationWarnings(2)
        succeeds ":uploadArchives"

        and:
        skipped(":signArchives")

        and:
        jarUploaded()
        signatureNotUploaded()
        pom().exists()
        !pomSignature().exists()

        where:
        signingProperty << ['signing.keyId', 'signing.password', 'signing.secretKeyRingFile']
    }
}
