/*
 * Copyright 2018 the original author or authors.
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

class SigningDistributionsIntegrationSpec extends SigningIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/7580")
    def "can sign a distribution zip when distribution plugin is applied after signing is configured"() {
        given:
        buildFile << """
            signing {
                ${signingConfiguration()}
                sign configurations.archives
            }

            apply plugin: "distribution"

            distributions {
                main {
                    distributionBaseName = 'main'
                    contents.from "src/main/java"
                }
            }

            ${keyInfo.addAsPropertiesScript()}
            ${getJavadocAndSourceJarsScript("archives")}
        """

        when:
        run "buildSignatures"

        then:
        executedAndNotSkipped ":signArchives"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
        file("build", "libs", "sign-1.0-javadoc.jar.asc").text
        file("build", "libs", "sign-1.0-sources.jar.asc").text
        file("build", "distributions", "main-1.0.zip.asc").text
        file("build", "distributions", "main-1.0.tar.asc").text
    }
}
