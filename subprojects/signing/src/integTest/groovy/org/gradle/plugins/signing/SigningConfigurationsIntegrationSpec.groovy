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


import spock.lang.Issue

import java.nio.file.Files

class SigningConfigurationsIntegrationSpec extends SigningIntegrationSpec {

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

    @Issue([
        "https://github.com/gradle/gradle/issues/21857",
        "https://github.com/gradle/gradle/issues/22375"
    ])
    def "signing configuration should be idempotent"() {
        given:
        buildFile << """
            configurations {
                meta
            }

            signing {
                ${signingConfiguration()}
                sign configurations.archives, configurations.archives
            }

            ${keyInfo.addAsPropertiesScript()}
            ${getJavadocAndSourceJarsScript("meta")}
        """

        when:
        run "buildSignatures"

        then:
        executedAndNotSkipped ":signArchives"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
    }

    @Issue("gradle/gradle#4980")
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
