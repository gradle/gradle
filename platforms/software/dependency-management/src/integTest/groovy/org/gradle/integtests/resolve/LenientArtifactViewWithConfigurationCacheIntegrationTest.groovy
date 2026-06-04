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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

/**
 * Tests the behavior of {@link org.gradle.api.artifacts.ArtifactView} configured to be lenient
 * when the configuration cache is enabled.
 *
 * Each scenario mixes a successful dependency with a failing one so the assertions confirm that
 * successful artifacts coexist with skipped failures, not that everything was silently dropped.
 * The CC store path (first invocation) and the CC load path (second invocation) are both
 * exercised — lenient-related state is serialized into the cache and any flaw in deserialization
 * only surfaces on the second invocation.
 */
@Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "handles CC explicitly")
@Issue("https://github.com/gradle/gradle/issues/37420")
class LenientArtifactViewWithConfigurationCacheIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    private void withBasicProject() {
        buildFile """
            plugins {
                id("jvm-ecosystem")
            }
            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                }
            }
            tasks.register("resolveLenientFiles") {
                def files = configurations.res.incoming.artifactView {
                    lenient = true
                }.files
                doLast {
                    println "files = " + files*.name.sort()
                }
            }
            tasks.register("resolveLenientArtifacts") {
                def artifacts = configurations.res.incoming.artifactView {
                    lenient = true
                }.artifacts
                doLast {
                    println "files = " + artifacts*.file*.name.sort()
                }
            }
            tasks.register("printFailures") {
                def artifacts = configurations.res.incoming.artifactView {
                    lenient = true
                }.artifacts
                doLast {
                    artifacts.failures.each { println("failure: " + it.message) }
                }
            }
        """
    }

    def "lenient artifact view keeps successful artifacts when a dependency does not exist across CC store/load (#task)"() {
        def good = mavenHttpRepo.module("org", "good").publish()
        def missing = mavenHttpRepo.module("org", "missing")

        withBasicProject()
        withRepo()
        buildFile """
            dependencies {
                deps("org:good:1.0")
                deps("org:missing:1.0")
            }
        """

        when:
        good.pom.expectGet()
        good.artifact.expectGet()
        missing.pom.expectGetMissing()
        succeeds(task)

        then:
        configurationCache.assertStateStored()
        outputContains("files = [good-1.0.jar]")

        when:
        succeeds(task)

        then:
        configurationCache.assertStateLoaded()
        outputContains("files = [good-1.0.jar]")

        where:
        task << ["resolveLenientFiles", "resolveLenientArtifacts"]
    }

    def "lenient artifact view keeps successful artifacts when one artifact download is broken across CC store/load (#task)"() {
        def good = mavenHttpRepo.module("org", "good").publish()
        def broken = mavenHttpRepo.module("org", "broken").publish()

        withBasicProject()
        withRepo()
        buildFile """
            dependencies {
                deps("org:good:1.0")
                deps("org:broken:1.0")
            }
        """

        when:
        good.pom.expectGet()
        good.artifact.expectGet()
        broken.pom.expectGet()
        broken.artifact.expectGetBroken()
        succeeds(task)

        then:
        configurationCache.assertStateStored()
        outputContains("files = [good-1.0.jar]")

        when:
        succeeds(task)

        then:
        configurationCache.assertStateLoaded()
        outputContains("files = [good-1.0.jar]")

        where:
        task << ["resolveLenientFiles", "resolveLenientArtifacts"]
    }

    def "lenient artifact view failures are preserved across CC store/load even when other dependencies succeed"() {
        def good = mavenHttpRepo.module("org", "good").publish()
        def missing = mavenHttpRepo.module("org", "missing")

        withBasicProject()
        withRepo()
        buildFile """
            dependencies {
                deps("org:good:1.0")
                deps("org:missing:1.0")
            }
        """

        when:
        good.pom.expectGet()
        good.artifact.expectGet()
        missing.pom.expectGetMissing()
        succeeds("printFailures")

        then:
        configurationCache.assertStateStored()
        outputContains("failure: Could not find org:missing:1.0")

        when:
        succeeds("printFailures")

        then:
        configurationCache.assertStateLoaded()
        outputContains("failure: Could not find org:missing:1.0")
    }

    def "ArtifactCollection accessors work after CC load when a lenient failure is present"() {
        def good = mavenHttpRepo.module("org", "good").publish()
        def missing = mavenHttpRepo.module("org", "missing")

        withBasicProject()
        withRepo()
        buildFile """
            dependencies {
                deps("org:good:1.0")
                deps("org:missing:1.0")
            }
            tasks.register("inspectArtifactCollection") {
                def artifacts = configurations.res.incoming.artifactView {
                    lenient = true
                }.artifacts
                def artifactFiles = artifacts.artifactFiles
                def resolvedArtifacts = artifacts.resolvedArtifacts
                doLast {
                    println "artifacts = " + artifacts*.file*.name.sort()
                    println "artifactFiles = " + artifactFiles*.name.sort()
                    println "resolvedArtifacts = " + resolvedArtifacts.get()*.file*.name.sort()
                    println "failures = " + artifacts.failures*.message.sort()
                }
            }
        """

        when:
        good.pom.expectGet()
        good.artifact.expectGet()
        missing.pom.expectGetMissing()
        succeeds("inspectArtifactCollection")

        then:
        configurationCache.assertStateStored()
        outputContains("artifacts = [good-1.0.jar]")
        outputContains("artifactFiles = [good-1.0.jar]")
        outputContains("resolvedArtifacts = [good-1.0.jar]")
        outputContains("failures = [Could not find org:missing:1.0")

        when:
        succeeds("inspectArtifactCollection")

        then:
        configurationCache.assertStateLoaded()
        outputContains("artifacts = [good-1.0.jar]")
        outputContains("artifactFiles = [good-1.0.jar]")
        outputContains("resolvedArtifacts = [good-1.0.jar]")
        outputContains("failures = [Could not find org:missing:1.0")
    }

    def "lenient artifact view permits transform with failed input across CC store/load"() {
        def good = mavenHttpRepo.module("org", "good").publish()
        def broken = mavenHttpRepo.module("org", "broken").publish()

        buildFile """
            import org.gradle.api.artifacts.transform.TransformAction
            import org.gradle.api.artifacts.transform.TransformOutputs
            import org.gradle.api.artifacts.transform.TransformParameters
            import org.gradle.api.artifacts.transform.InputArtifact
            import org.gradle.api.file.FileSystemLocation
            import org.gradle.api.provider.Provider

            plugins {
                id("jvm-ecosystem")
            }

            def artifactType = Attribute.of('artifactType', String)

            abstract class IdentityTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()
                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "transforming " + input.name
                    outputs.file(input)
                }
            }

            configurations {
                dependencyScope("deps")
                resolvable("res") {
                    extendsFrom(deps)
                }
            }
            dependencies {
                registerTransform(IdentityTransform) {
                    from.attribute(artifactType, "jar")
                    to.attribute(artifactType, "transformed")
                }
                deps("org:good:1.0")
                deps("org:broken:1.0")
            }
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
            tasks.register("resolveLenientTransform") {
                def files = configurations.res.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'transformed') }
                    lenient = true
                }.files
                doLast {
                    println "files = " + files*.name.sort()
                }
            }
        """

        when:
        good.pom.expectGet()
        good.artifact.expectGet()
        broken.pom.expectGet()
        broken.artifact.expectGetBroken()
        succeeds("resolveLenientTransform")

        then:
        configurationCache.assertStateStored()
        outputContains("transforming good-1.0.jar")
        outputDoesNotContain("transforming broken-1.0.jar")
        outputContains("files = [good-1.0.jar]")

        when:
        // Transform output is cached on disk, so the second invocation reuses the result without
        // re-executing the transform action — assert only on the resolved files here.
        succeeds("resolveLenientTransform")

        then:
        configurationCache.assertStateLoaded()
        outputContains("files = [good-1.0.jar]")
    }

    private void withRepo() {
        buildFile << """
            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }
        """
    }
}
