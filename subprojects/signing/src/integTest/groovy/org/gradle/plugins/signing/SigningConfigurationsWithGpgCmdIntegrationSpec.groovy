/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.SigningTestPreconditions

@Requires(SigningTestPreconditions.GpgAvailable)
class SigningConfigurationsWithGpgCmdIntegrationSpec extends SigningConfigurationsIntegrationSpec {
    SignMethod getSignMethod() {
        return SignMethod.GPG_CMD
    }

    def "does not leak passphrase at info logging"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}
            signing {
                useGpgCmd()
                sign(jar)
                signatories = new ${GnupgSignatoryProvider.name}()
            }
        """

        setupGpgCmd()

        when:
        run "signJar", "-i"

        then:
        executedAndNotSkipped(":signJar")
        assertDoesNotLeakPassphrase()
    }

    private void assertDoesNotLeakPassphrase() {
        outputContains("--passphrase-fd 0")
        result.assertNotOutput("--passphrase ")
    }
}
