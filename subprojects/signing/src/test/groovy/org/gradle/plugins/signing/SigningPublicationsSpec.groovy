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

import org.gradle.api.publish.maven.MavenPublication

class SigningPublicationsSpec extends SigningProjectSpec {

    def setup() {
        applyPlugin()
        apply plugin: "maven-publish"
        useJavadocAndSourceJars()
        publishing {
            publications {
                maven(MavenPublication) {
                    from components.java
                    artifact sourcesJar
                    artifact javadocJar
                }
            }
        }
    }

    def "publication added to container after signing is specified"() {
        given:
        def signTasks = []
        signing {
            signTasks = sign publishing.publications
        }

        when:
        publishing.publications {
            another(MavenPublication) {
                from components.java
            }
        }

        then:
        tasks.findByName('signMavenPublication') != null
        tasks.findByName('signAnotherPublication') != null

        and:
        signTasks == [signMavenPublication, signAnotherPublication]
    }

    def "publication removed from container after signing is specified"() {
        given:
        def signTasks = []
        signing {
            signTasks = sign publishing.publications
        }

        when:
        publishing.publications.clear()

        then:
        !tasks.findByName('signMavenPublication').enabled

        and:
        signTasks.isEmpty()
    }

    def "sign task has description"() {
        when:
        signing {
            sign publishing.publications
        }

        then:
        signMavenPublication.description == "Signs all artifacts in the 'maven' publication."
    }
}
