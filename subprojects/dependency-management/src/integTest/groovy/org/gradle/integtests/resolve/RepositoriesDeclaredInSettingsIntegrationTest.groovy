/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

// Restrict the number of combinations because that's not really what we want to test
@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class RepositoriesDeclaredInSettingsIntegrationTest extends AbstractModuleDependencyResolveTest {
    boolean isDeclareRepositoriesInSettings() {
        true
    }

    def "can declare dependency in settings for a single-project build"() {
        repository {
            'org:module:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:module:1.0')
            }
        }
    }

    def "can declare dependency in settings for a multi-project build"() {
        repository {
            "org:module-lib1:1.0"()
            "org:module-lib2:1.0"()
        }
        settingsFile << """
            include 'lib1', 'lib2'
        """

        buildFile << """
            dependencies {
                conf project(path:":lib1", configuration: 'conf')
                conf project(path:":lib2", configuration: 'conf')
            }
        """

        ['lib1', 'lib2'].each {
            file("${it}/build.gradle") << """
                configurations {
                    conf
                }

                dependencies {
                    conf 'org:module-${it}:1.0'
                }
            """
        }

        when:
        repositoryInteractions {
            'org:module-lib1:1.0' {
                expectResolve()
            }
            'org:module-lib2:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":lib1", "test:lib1:") {
                    configuration = 'conf'
                    noArtifacts()
                    module('org:module-lib1:1.0')
                }
                project(":lib2", "test:lib2:") {
                    configuration = 'conf'
                    noArtifacts()
                    module('org:module-lib2:1.0')
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "failing builds are not handled properly")
    def "project local repositories override whatever is in settings"() {
        repository {
            'org:module:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }

            repositories {
                maven { url 'dummy' }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not find org:module:1.0.")
    }

    @ToBeFixedForConfigurationCache(because = "missing support for composite builds")
    def "repositories declared in settings are used to resolve dependencies from included builds"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
        """
        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", "project :included", "com.acme:included:0.x") {
                    configuration = 'default'
                    compositeSubstitute()
                    noArtifacts()
                    module('org:module:1.0')
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "missing support for composite builds")
    def "repositories declared in settings are used to resolve dependencies from nested included builds"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'com.acme:nested:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
            includeBuild '../nested'
        """

        file("nested/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("nested/settings.gradle") << """
            rootProject.name = 'nested'
        """

        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", "project :included", "com.acme:included:0.x") {
                    configuration = 'default'
                    compositeSubstitute()
                    noArtifacts()
                    edge("com.acme:nested:1.0", "project :nested", "com.acme:nested:0.x") {
                        configuration = 'default'
                        compositeSubstitute()
                        noArtifacts()
                        module('org:module:1.0')
                    }
                }
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "missing support for composite builds")
    def "repositories declared in nested included build settings are ignored"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'com.acme:nested:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
            includeBuild '../nested'
        """

        file("nested/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("nested/settings.gradle") << """
            rootProject.name = 'nested'

            dependencyResolutionManagement {
                repositories {
                    maven {
                        url "this should be ignored"
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", "project :included", "com.acme:included:0.x") {
                    configuration = 'default'
                    compositeSubstitute()
                    noArtifacts()
                    edge("com.acme:nested:1.0", "project :nested", "com.acme:nested:0.x") {
                        configuration = 'default'
                        compositeSubstitute()
                        noArtifacts()
                        module('org:module:1.0')
                    }
                }
            }
        }
    }

    def "repositories declared in settings are ignored when resolving dependencies from included builds with explicit project repositories"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
        """
        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }

            repositories {
                maven { url 'dummy' }
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not find org:module:1.0.")
    }
}
