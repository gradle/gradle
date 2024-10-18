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

import org.gradle.plugins.signing.type.pgp.ArmoredSignatureType

class SigningConfigurationsSpec extends SigningProjectSpec {

    def setup() {
        applyPlugin()
        useJavadocAndSourceJars()
        project.configurations {
            meta
            produced.extendsFrom meta, archives
        }

        project.artifacts {
            meta project.javadocJar, project.sourcesJar
        }
    }

    def "sign configuration with defaults"() {
        when:
        signing {
            sign configurations.archives, configurations.meta
        }

        then:
        def signingTasks = [project.signArchives, project.signMeta]

        // TODO - find way to test that the appropriate dependencies have been setup
        //        it would be easy if we could do…
        //
        // configurations.archives.buildArtifacts in signArchives.dependsOn
        //
        //        but we can't because of https://issues.gradle.org/browse/GRADLE-1608

        and:
        configurations.signatures.artifacts.size() == 3
        signingTasks.every { it.signatures.every { it in configurations.signatures.artifacts } }
    }

    def "sign configuration with inherited artifacts"() {
        when:
        signing {
            sign configurations.produced
        }

        then:
        configurations.signatures.artifacts.size() == 3
        project.signProduced.signatures.every { it in configurations.signatures.artifacts }
    }

    def "sign configuration with custom type"() {
        def signingTasks
        when:
        signing {
            signingTasks = sign configurations.produced
            signingTasks[0].signatureType new ArmoredSignatureType()
        }

        then:
        signingTasks[0].getSignatureType() instanceof ArmoredSignatureType
    }

    def "sign task has description"() {
        when:
        signing {
            sign project.configurations.produced
        }

        then:
        project.signProduced.description == "Signs all artifacts in the 'produced' configuration."
    }
}
