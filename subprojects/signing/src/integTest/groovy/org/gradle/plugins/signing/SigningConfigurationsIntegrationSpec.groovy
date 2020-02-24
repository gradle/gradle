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

import java.nio.file.Files

class SigningConfigurationsIntegrationSpec extends SigningIntegrationSpec {

    @ToBeFixedForInstantExecution
    def "signing configurations"() {
        given:
        buildFile << """
            configurations {
                meta
            }
            
            signing {
                ${signingConfiguration()}
                sign configurations.archives, configurations.meta
            }

            ${keyInfo.addAsPropertiesScript()}
            ${getJavadocAndSourceJarsScript("meta")}
        """

        when:
        run "buildSignatures"

        then:
        executedAndNotSkipped ":signArchives", ":signMeta"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "libs", "sign-1.0-javadoc.jar.asc").text
        file("build", "libs", "sign-1.0-sources.jar.asc").text
    }

    @Issue("gradle/gradle#4980")
    @ToBeFixedForInstantExecution
    def "removing signature file causes sign task to re-execute"() {
        given:
        buildFile << """
            signing {
                ${signingConfiguration()}
                sign configurations.archives
            }

            ${keyInfo.addAsPropertiesScript()}
        """

        when:
        run "sign"

        then:
        executedAndNotSkipped ":signArchives"

        when:
        Files.delete(file("build", "libs", "sign-1.0.jar.asc").toPath())

        and:
        run "sign"

        then:
        executedAndNotSkipped ":signArchives"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
    }

    @ToBeFixedForInstantExecution
    def "duplicated inputs are handled"() {
        given:
        buildFile << """
            signing {
                ${signingConfiguration()}
                sign configurations.archives
            }

            ${keyInfo.addAsPropertiesScript()}

            artifacts {
                // depend directly on 'jar' task in addition to dependency through 'archives'
                archives jar  
            }
        """

        when:
        run "buildSignatures"

        then:
        executedAndNotSkipped ":signArchives"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
    }
}
