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

class SigningClosureBlockIntegrationSpec extends SigningIntegrationSpec {

    def "use groovy closure when signing"() {
        given:
        buildFile << """
            ${keyInfo.addAsPropertiesScript()}

            signing {
                sign {
                    sign(tasks.jar)
                }
            }
        """

        when:
        run "sign"

        then:
        executedAndNotSkipped ":signJar"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
    }

    def "use kotlin action when signing"() {
        given:
        buildFile.delete()
        buildKotlinFile << """
            plugins {
                `java-library`
                signing
            }

            ${keyInfo.addAsKotlinPropertiesScript()}

            base {
                archivesName.set("${artifactId}")
            }
            group = "sign"
            version = "$version"

            signing {
                sign {
                    sign(tasks.jar.get())
                }
            }
        """
        println buildKotlinFile.text

        when:
        run "sign"

        then:
        executedAndNotSkipped ":signJar"

        and:
        file("build", "libs", "sign-1.0.jar.asc").text
    }


}
