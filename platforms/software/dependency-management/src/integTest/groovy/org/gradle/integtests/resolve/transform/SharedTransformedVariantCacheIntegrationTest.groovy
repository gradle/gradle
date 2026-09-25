/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

/**
 * Coverage for the build-tree-wide sharing of transformed external artifact variants
 * ({@link org.gradle.api.internal.artifacts.transform.SharedTransformedVariantCache}).
 */
class SharedTransformedVariantCacheIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {

    /**
     * Regression coverage for the failure class of cc392c15aad: transform parameter file collections can
     * contain values that are produced lazily, such as the output of another artifact transform. On the
     * shared path, parameters are isolated when the entry is created during dependency resolution, so the
     * transform must still observe the final parameter values.
     *
     * Note that parameters backed by task outputs are only wired for the project artifact path (which keeps
     * lazy isolation); the external artifact path never schedules tasks for transform parameters.
     */
    @ToBeFixedForIsolatedProjects(because = "ArtifactTransformTestFixture is not IP compatible")
    def "transform with another transform output as file parameter on external artifacts observes final values"() {
        given:
        def lib = withColorVariants(mavenRepo.module("group", "lib", "1.0")).publish()
        lib.artifactFile.text = "lib-content"
        def tool = withColorVariants(mavenRepo.module("group", "tool", "1.0")).publish()
        tool.artifactFile.text = "tool-content"

        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransformWithAnotherTransformOutputAsInput()
        buildFile << """
            allprojects {
                repositories {
                    maven {
                        url = '${mavenRepo.uri}'
                        metadataSources { gradleMetadata() }
                    }
                }
                dependencies {
                    implementation 'group:lib:1.0'
                    transform 'group:tool:1.0'
                }
            }
        """

        when:
        run(":a:resolve", ":b:resolve")

        then:
        outputContains("processing tool-1.0.jar to make red")
        outputContains("processing lib-1.0.jar using [tool-1.0.jar.red]")
        output.count("result = [lib-1.0.jar.green]") == 2

        when: "second run reuses the cached transform results"
        run(":a:resolve", ":b:resolve")

        then:
        output.count("processing") == 0
        output.count("result = [lib-1.0.jar.green]") == 2
    }

    @ToBeFixedForIsolatedProjects(because = "ArtifactTransformTestFixture is not IP compatible")
    def "shared transformed variants produce correct results when consumed from several scopes"() {
        given:
        def module = withColorVariants(mavenRepo.module("group", "lib", "1.0")).publish()
        module.artifactFile.text = "lib-content"

        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithColorTransformImplementation()
        buildFile << """
            project(':a') {
                ${externalLibDependency()}
            }
            project(':b') {
                ${externalLibDependency()}
            }
        """

        when:
        run(":a:resolve", ":b:resolve")

        then:
        outputContains("processing [lib-1.0.jar]")
        result.assertTasksScheduled(":a:resolve", ":b:resolve")
    }

    @ToBeFixedForIsolatedProjects(because = "ArtifactTransformTestFixture is not IP compatible")
    def "per-scope variant cache opt-out property is honored"() {
        given:
        def module = withColorVariants(mavenRepo.module("group", "lib", "1.0")).publish()
        module.artifactFile.text = "lib-content"

        createDirs("a", "b")
        settingsFile << """
            include 'a', 'b'
        """
        propertiesFile << """
            org.gradle.artifact-transforms.unsafe.per-scope-variant-cache=true
        """
        setupBuildWithColorTransformImplementation()
        buildFile << """
            project(':a') {
                ${externalLibDependency()}
            }
            project(':b') {
                ${externalLibDependency()}
            }
        """

        when:
        run(":a:resolve", ":b:resolve")

        then:
        outputContains("processing [lib-1.0.jar]")
    }

    private String externalLibDependency() {
        """
            repositories {
                maven {
                    url = '${mavenRepo.uri}'
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                implementation 'group:lib:1.0'
            }
            task showContent {
                def view = configurations.resolver.incoming.artifactView { attributes.attribute(color, 'green') }.files
                inputs.files(view)
                doLast {
                    view.files.each { println "content of \${it.name}: \${it.text}" }
                }
            }
        """
    }
}
