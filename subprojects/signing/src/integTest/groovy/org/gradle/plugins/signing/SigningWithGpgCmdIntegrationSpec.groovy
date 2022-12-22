/*
 * Copyright 2022 the original author or authors.
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


import org.gradle.util.Requires

@Requires(adhoc = { GpgCmdFixture.getAvailableGpg() != null })
class SigningWithGpgCmdIntegrationSpec extends SigningIntegrationSpec {

    SignMethod getSignMethod() {
        return SignMethod.GPG_CMD
    }

    def "uses the default signatory"() {
        given:
        buildFile << """
            signing {
                useGpgCmd()
                sign(jar)
            }
        """

        // Remove the 'signing.gnupg.keyName' entry from the gradle.properties file, so the default key is picked up
        Properties properties = new Properties()
        properties.load(propertiesFile.newInputStream())
        properties.remove("signing.gnupg.keyName")
        properties.store(propertiesFile.newOutputStream(), "")

        when:
        run "signJar", "-i"

        then:
        executedAndNotSkipped(":signJar")
    }

}
