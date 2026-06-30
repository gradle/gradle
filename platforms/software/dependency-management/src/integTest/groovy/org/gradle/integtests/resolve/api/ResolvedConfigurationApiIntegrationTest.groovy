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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache

@FluidDependenciesResolveTest
class ResolvedConfigurationApiIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    def "artifacts may have no extension"() {
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
        m1.artifact(type: 'jar', ext: '')
        m1.artifact(type: '', ext: '')
        m1.artifact(type: '', ext: '', classifier: 'classy')
        m1.publish()

        buildFile << """
            repositories { ivy { url = '$ivyHttpRepo.uri' } }

            configurations {
                compile
                "default" {
                    extendsFrom compile
                }
            }

            dependencies {
                compile 'org:test:1.0'
            }

            task show {
                def legacyArtifacts = configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.iterator().next().moduleArtifacts
                def legacyNames = legacyArtifacts.collect { "\$it.name:\$it.extension:\$it.type" }
                def legacyClassifiers = legacyArtifacts.collect { it.classifier }

                def resolvedArtifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
                dependsOn(resolvedArtifacts)
                doLast {
                    println "files: " + resolvedArtifacts.get().collect { it.file.name }
                    println "display-names: " + resolvedArtifacts.get().collect { it.toString() }
                    println "ids: " + resolvedArtifacts.get().collect { it.id.displayName }
                    println "names: " + legacyNames
                    println "classifiers: " + legacyClassifiers
                }
            }
        """

        when:
        m1.ivy.expectGet()
        // Only request once, both artifacts have the same url and only differ by type. Should probably be the same artifact
        m1.getArtifact(type: '', ext: '').expectGet()
        m1.getArtifact(type: '', ext: '', classifier: 'classy').expectGet()

        run 'show'

        then:
        outputContains("files: [test-1.0, test-1.0, test-1.0-classy]")
        outputContains("display-names: [test-1.0 (org:test:1.0), test-1.0 (org:test:1.0), test-1.0-classy (org:test:1.0)]")
        outputContains("ids: [test-1.0 (org:test:1.0), test-1.0 (org:test:1.0), test-1.0-classy (org:test:1.0)]")
        outputContains("names: [test::, test::jar, test::]")
        outputContains("classifiers: [null, null, classy]")
    }

    @UnsupportedWithConfigurationCache(because = "task exercises the ResolvedConfiguration API, which is a deprecated API that we don't want to invest in making compatible with configuration cache")
    def "reports multiple failures to resolve components"() {
        buildFile << """
            repositories { maven { url = '${mavenHttpRepo.uri}' } }

            configurations {
                compile
                "default" {
                    extendsFrom compile
                }
            }

            dependencies {
                compile 'test:test1:1.2'
                compile 'test:test2:1.2'
                compile 'test:test3:1.2'
            }

            task show {
                def artifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
                doLast {
                    artifacts.get()
                }
            }
        """

        when:
        def m1 = mavenHttpRepo.module("test", "test1", "1.2")
        m1.pom.expectGetMissing()
        def m2 = mavenHttpRepo.module("test", "test2", "1.2")
        m2.pom.expectGetUnauthorized()
        def m3 = mavenHttpRepo.module("test", "test3", "1.2").publish()
        m3.pom.expectGet()

        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show' (registered in build file 'build.gradle').")
        failure.assertHasCause("Could not resolve all artifacts for configuration ':compile'.")
        failure.assertHasCause("Could not find test:test1:1.2.")
        failure.assertHasCause("Could not resolve test:test2:1.2.")
    }

    @UnsupportedWithConfigurationCache(because = "task exercises the ResolvedConfiguration API, which is a deprecated API that we don't want to invest in making compatible with configuration cache")
    def "reports failure to resolve artifact"() {
        buildFile << """
            repositories { maven { url = '${mavenHttpRepo.uri}' } }

            configurations {
                compile
                "default" {
                    extendsFrom compile
                }
            }

            dependencies {
                compile 'test:test1:1.2'
                compile 'test:test2:1.2'
                compile 'test:test3:1.2'
            }

            task show {
                def artifacts = configurations.compile.incoming.artifacts.resolvedArtifacts
                doLast {
                    artifacts.get()
                }
            }
        """

        when:
        def m1 = mavenHttpRepo.module("test", "test1", "1.2").publish()
        m1.pom.expectGet()
        m1.artifact.expectGetMissing()
        def m2 = mavenHttpRepo.module("test", "test2", "1.2").publish()
        m2.pom.expectGet()
        m2.artifact.expectGet()
        def m3 = mavenHttpRepo.module("test", "test3", "1.2").publish()
        m3.pom.expectGet()
        m3.artifact.expectGet()

        fails("show")

        then:
        failure.assertHasDescription("Execution failed for task ':show' (registered in build file 'build.gradle').")
        failure.assertHasCause("Could not find test1-1.2.jar (test:test1:1.2).")
    }
}
