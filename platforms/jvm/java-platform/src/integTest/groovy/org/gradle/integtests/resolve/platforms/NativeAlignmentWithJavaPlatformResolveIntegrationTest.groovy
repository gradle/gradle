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

package org.gradle.integtests.resolve.platforms

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

import static org.gradle.util.internal.TextUtil.escapeString

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value="true")
@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value="maven")
class NativeAlignmentWithJavaPlatformResolveIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "publishes a platform with native alignment"() {
        settingsFile << """
            rootProject.name = 'root'
            include "platform"
            include "core"
            include "lib"
        """
        file("platform/build.gradle") << """
            plugins {
                id 'java-platform'
            }

            dependencies {
                constraints {
                    api(project(":core")) { because "platform alignment" }
                    api(project(":lib")) { because "platform alignment" }
                }
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.javaPlatform
                    }
                }
            }
        """
        file("core/build.gradle") << """
            plugins {
                id 'java-library'
            }
            dependencies {
                api(platform(project(":platform")))
                api(project(":lib"))
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
        file("lib/build.gradle") << """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }
            dependencies {
                api(platform(project(":platform")))
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """
        file("build.gradle") << """
            allprojects {
                group = 'com.acme.foo'
                version = rootProject.getProperty('ver')
            }
            subprojects {
                apply plugin: 'maven-publish'
                publishing {
                   repositories {
                       maven { url = "${escapeString(mavenRepo.rootDir)}" }
                   }
                }
            }
        """

        when:
        run "publishAllPublicationsToMavenRepo", "-Pver=1.0"
        run "publishAllPublicationsToMavenRepo", "-Pver=1.1"

        then:
        ['1.0', '1.1'].each { v ->
            def platform = mavenRepo.module("com.acme.foo", "platform", v)
            platform.assertPublished()
            platform.hasGradleMetadataRedirectionMarker()
            platform.parsedModuleMetadata.variant("apiElements") {
                constraint("com.acme.foo:core:$v") {
                    exists()
                }
                constraint("com.acme.foo:lib:$v") {
                    exists()
                }
                noMoreDependencies()
            }
            def core = mavenRepo.module("com.acme.foo", "core", v)
            core.assertPublished()
            core.hasGradleMetadataRedirectionMarker()
            core.parsedModuleMetadata.variant("apiElements") {
                dependency("com.acme.foo:lib:$v") {
                    exists()
                }
                dependency("com.acme.foo:platform:$v") {
                    exists()
                }
                noMoreDependencies()
            }
            def lib = mavenRepo.module("com.acme.foo", "lib", v)
            lib.assertPublished()
            lib.hasGradleMetadataRedirectionMarker()
            lib.parsedModuleMetadata.variant("apiElements") {
                dependency("com.acme.foo:platform:$v") {
                    exists()
                }
                noMoreDependencies()
            }
        }

        when:
        settingsFile.text = """
            rootProject.name = 'consumer'
        """

        buildFile.text = """
            apply plugin: 'java-library'
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
            dependencies {
                implementation("com.acme.foo:core:1.0")
                implementation("com.acme.foo:lib:1.1")
            }
        """
        resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()

        repositoryInteractions {
            'com.acme.foo' {
                'core' {
                    '1.0' {
                        expectGetMetadata()
                    }
                    '1.1' {
                        expectResolve()
                    }
                }
                'lib' {
                    '1.1' {
                        expectResolve()
                    }
                }
                'platform' {
                    '1.0' {
                        expectGetMetadata()
                    }
                    '1.1' {
                        expectGetMetadata()
                    }
                }

            }
        }

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":consumer:") {
                edge('com.acme.foo:core:1.0', 'com.acme.foo:core:1.1') {
                    byConstraint("platform alignment")
                    byConflictResolution("between versions 1.1 and 1.0")
                    variant "apiElements", [
                        'org.gradle.category':'library',
                        'org.gradle.dependency.bundling':'external',
                        'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                        'org.gradle.status':'release',
                        'org.gradle.usage': 'java-api',
                        'org.gradle.libraryelements': 'jar',
                    ]
                    module('com.acme.foo:platform:1.1') {
                        variant "apiElements", [
                            'org.gradle.category':'platform',
                            'org.gradle.status':'release',
                            'org.gradle.usage': 'java-api']
                        constraint('com.acme.foo:core:1.1')
                        constraint('com.acme.foo:lib:1.1')
                        byConflictResolution("between versions 1.1 and 1.0")
                        noArtifacts()
                    }
                    module('com.acme.foo:lib:1.1') {
                        variant "apiElements", [
                            'org.gradle.category':'library',
                            'org.gradle.dependency.bundling':'external',
                            'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                            'org.gradle.status':'release',
                            'org.gradle.usage': 'java-api',
                            'org.gradle.libraryelements': 'jar',
                        ]
                        byConflictResolution("between versions 1.1 and 1.0")
                        byConstraint("platform alignment")
                    }
                }
                module('com.acme.foo:lib:1.1') {
                    variant "apiElements", [
                        'org.gradle.category':'library',
                        'org.gradle.dependency.bundling':'external',
                        'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                        'org.gradle.status':'release',
                        'org.gradle.usage': 'java-api',
                        'org.gradle.libraryelements': 'jar',
                    ]
                    module('com.acme.foo:platform:1.1') {
                        variant "apiElements", [
                            'org.gradle.category':'platform',
                            'org.gradle.status':'release',
                            'org.gradle.usage': 'java-api']
                    }
                }
            }
        }
    }
}
