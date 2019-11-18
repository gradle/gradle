/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.maven.MavenJavaModule

class MavenPublishJavaIntegTest extends AbstractMavenPublishJavaIntegTest {

    boolean withDocs() {
        false
    }

    List<String> features() {
        [MavenJavaModule.MAIN_FEATURE]
    }

    @ToBeFixedForInstantExecution
    def "can publish java-library without warning when dependency with maven incompatible version and using versionMapping"() {
        given:
        createBuildScripts("""

            ${jcenterRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:1.+"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        versionMapping {
                            allVariants {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputDoesNotContain(DefaultMavenPublication.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()

        javaLibrary.parsedPom.scopes.keySet() == ["runtime"] as Set
        javaLibrary.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:1.0")

        and:
        javaLibrary.parsedModuleMetadata.variant("apiElements") {
            noMoreDependencies()
        }

        javaLibrary.parsedModuleMetadata.variant("runtimeElements") {
            dependency("commons-collections:commons-collections:1.0") {
                rejects()
                noMoreExcludes()
            }
            noMoreDependencies()
        }
    }

    @ToBeFixedForInstantExecution
    def "a component's variant can be modified before publishing"() {
        given:
        createBuildScripts """
            dependencies {
                api 'org:foo:1.0'
                implementation 'org:bar:1.0'
            }
            components.java.withVariantsFromConfiguration(configurations.runtimeElements) {
                skip()
            }
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        with(javaLibrary.parsedPom) {
            assert scopes.size() == 1
            scopes['compile'].expectDependency('org:foo:1.0')
        }
        with(javaLibrary.parsedModuleMetadata) {
            assert variants.size() == 1
            assert variants[0].name == "apiElements"
            assert variants[0].dependencies*.coords == ["org:foo:1.0"]
        }
    }

    @ToBeFixedForInstantExecution
    def "can ignore all publication warnings by variant name"() {
        given:
        def silenceMethod = "suppressPomMetadataWarningsFor"
        createBuildScripts("""

            configurations.api.outgoing.capability 'org:foo:1.0'
            configurations.implementation.outgoing.capability 'org:bar:1.0'

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        $silenceMethod('runtimeElements')
                        $silenceMethod('apiElements')
                    }
                }
            }
        """)

        when:
        run "publish"

        then:
        outputDoesNotContain(DefaultMavenPublication.PUBLICATION_WARNING_FOOTER)
        javaLibrary.assertPublished()
    }

}
