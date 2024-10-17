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
        project.apply plugin: "maven-publish"
        useJavadocAndSourceJars()
        project.publishing {
            publications {
                maven(MavenPublication) {
                    from project.components.java
                    artifact project.sourcesJar
                    artifact project.javadocJar
                }
            }
        }
    }

    def "publication added to container after signing is specified"() {
        given:
        def signTasks = []
        signing {
            signTasks = sign project.publishing.publications
        }

        when:
        project.publishing.publications {
            another(MavenPublication) {
                from project.components.java
            }
        }

        then:
        project.tasks.findByName('signMavenPublication') != null
        project.tasks.findByName('signAnotherPublication') != null

        and:
        signTasks == [project.signMavenPublication, project.signAnotherPublication]
    }

    def "publication removed from container after signing is specified"() {
        given:
        def signTasks = []
        signing {
            signTasks = sign project.publishing.publications
        }

        when:
        project.publishing.publications.clear()

        then:
        !project.tasks.named('signMavenPublication').get().enabled

        and:
        signTasks.isEmpty()
    }

    def "sign task has description"() {
        when:
        signing {
            sign project.publishing.publications
        }

        then:
        project.signMavenPublication.description == "Signs all artifacts in the 'maven' publication."
    }
}
