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
package org.gradle.integtests.resolve.maven

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

/**
 * Prototype coverage for Maven settings.xml mirror support, behind the
 * {@code org.gradle.internal.mavenMirrors} Gradle property.
 */
class MavenSettingsMirrorIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        buildFile << """
            configurations { compile }
            dependencies { compile 'org.test:projectA:1.0' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """
    }

    def "wildcard mirror from maven settings replaces mavenCentral when enabled"() {
        given:
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = mirrorRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Applying Maven mirror 'test-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo.uri}")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "mirror is not applied when the feature flag is off"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = originalRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        run 'retrieve'

        then:
        outputDoesNotContain("Applying Maven mirror")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "unsupported mirrorOf value emits a warning and is not applied"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        def module = originalRepo.module("org.test", "projectA", "1.0").publish()
        writeMirrorSettings(mirrorRepo.uri.toString(), "external:*", "selective")

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        and:
        module.pom.expectGet()
        module.artifact.expectGet()

        when:
        using m2
        executer.withArgument("-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Maven mirror 'selective' with mirrorOf 'external:*' is not supported and will be ignored (only '*' is supported).")
        outputDoesNotContain("Applying Maven mirror")
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "changing maven settings invalidates the configuration cache when mirrors are enabled"() {
        given:
        def mirrorRepo1 = mavenHttpRepo("mirror1")
        def mirrorRepo2 = mavenHttpRepo("mirror2")
        mirrorRepo1.module("org.test", "projectA", "1.0").publish().allowAll()
        mirrorRepo2.module("org.test", "projectA", "1.0").publish().allowAll()
        writeMirrorSettings(mirrorRepo1.uri.toString())

        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when:
        using m2
        executer.withArguments("--configuration-cache", "-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        outputContains("Applying Maven mirror 'test-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo1.uri}")

        when:
        writeMirrorSettings(mirrorRepo2.uri.toString())
        using m2
        executer.withArguments("--configuration-cache", "-Porg.gradle.internal.mavenMirrors=true")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry stored.")
        outputContains("Applying Maven mirror 'test-mirror' for repository 'MavenRepo': ${RepositoryHandler.MAVEN_CENTRAL_URL} -> ${mirrorRepo2.uri}")

        and: "the invalidation reason is only logged when not in quiet configuration cache mode"
        GradleContextualExecuter.configCache || output.contains("Maven settings.xml content has changed")
    }

    def "changing maven settings does not invalidate the configuration cache when mirrors are disabled"() {
        given:
        def originalRepo = mavenHttpRepo("original")
        def mirrorRepo = mavenHttpRepo("mirror")
        originalRepo.module("org.test", "projectA", "1.0").publish().allowAll()
        writeMirrorSettings(mirrorRepo.uri.toString())

        buildFile << """
            repositories {
                maven { url = '${originalRepo.uri}' }
            }
        """

        when:
        using m2
        executer.withArgument("--configuration-cache")
        run 'retrieve'

        then:
        outputDoesNotContain("Applying Maven mirror")

        when:
        writeMirrorSettings(mirrorRepo.uri.toString(), "*", "another-mirror")
        using m2
        executer.withArgument("--configuration-cache")
        run 'retrieve'

        then:
        postBuildOutputContains("Configuration cache entry reused.")
        outputDoesNotContain("Applying Maven mirror")
    }

    private void writeMirrorSettings(String mirrorUrl, String mirrorOf = "*", String id = "test-mirror") {
        m2.userSettingsFile.text = """
            <settings>
                <mirrors>
                    <mirror>
                        <id>${id}</id>
                        <mirrorOf>${mirrorOf}</mirrorOf>
                        <url>${mirrorUrl}</url>
                    </mirror>
                </mirrors>
            </settings>
        """
    }
}
