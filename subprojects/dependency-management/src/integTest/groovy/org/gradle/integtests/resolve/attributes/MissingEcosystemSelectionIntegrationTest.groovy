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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class MissingEcosystemSelectionIntegrationTest extends AbstractDependencyResolutionTest {

    ResolveTestFixture resolve

    def setup() {
        buildFile << """
            allprojects {
                apply plugin: 'java-library'
            }
        """
        settingsFile << """
            rootProject.name = 'test'
        """
        resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()
    }

    def "fails with a reasonable error message when consumer needs to apply an ecosystem plugin"() {
        given:
        mavenRepo.module("org", "foo")
                .withModuleMetadata()
                .withGradleMetadataRedirection()
                .variant('api', ['custom': 'v1'])
                .variant('runtime', [custom: 'v2'])
                .ecosystem('dummy')
                .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                api("org:foo:1.0")
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot choose between the following variants of org:foo:1.0:
  - api
  - runtime
All of them match the consumer attributes:
  - Variant 'api' capability org:foo:1.0:
      - Found custom 'v1' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
  - Variant 'runtime' capability org:foo:1.0:
      - Found custom 'v2' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
This error might be fixed by applying a plugin understanding the 'dummy' ecosystem."""

    }

    def "fails with a reasonable error message when consumer needs to apply an ecosystem plugin using transitive dependency"() {
        given:
        mavenRepo.module("org", "direct")
            .dependsOn("org", "foo", "1.0")
            .publish()
        mavenRepo.module("org", "foo")
                .withModuleMetadata()
                .withGradleMetadataRedirection()
                .variant('api', ['custom': 'v1'])
                .variant('runtime', [custom: 'v2'])
                .ecosystem('dummy')
                .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                api("org:direct:1.0")
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot choose between the following variants of org:foo:1.0:
  - api
  - runtime
All of them match the consumer attributes:
  - Variant 'api' capability org:foo:1.0:
      - Found custom 'v1' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
  - Variant 'runtime' capability org:foo:1.0:
      - Found custom 'v2' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
This error might be fixed by applying a plugin understanding the 'dummy' ecosystem."""

    }

    def "doesn't warn about missing ecosystem if registered"() {
        given:
        mavenRepo.module("org", "foo")
                .withModuleMetadata()
                .withGradleMetadataRedirection()
                .variant('api', ['custom': 'v1'])
                .variant('runtime', [custom: 'v2'])
                .ecosystem('dummy')
                .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                api("org:foo:1.0")
                attributesSchema.registerEcosystem('dummy', 'usually done by a plugin')
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot choose between the following variants of org:foo:1.0:
  - api
  - runtime
All of them match the consumer attributes:
  - Variant 'api' capability org:foo:1.0:
      - Found custom 'v1' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
  - Variant 'runtime' capability org:foo:1.0:
      - Found custom 'v2' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided."""

        and:
        !failure.error.contains('This error might be fixed by applying a plugin understanding the \'dummy\' ecosystem.')

    }

    def "can declare more than one ecosystem"() {
        given:
        mavenRepo.module("org", "foo")
                .withModuleMetadata()
                .withGradleMetadataRedirection()
                .variant('api', ['custom': 'v1'])
                .variant('runtime', [custom: 'v2'])
                .ecosystem('dummy')
                .ecosystem('other')
                .ecosystem('another')
                .publish()

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                api("org:foo:1.0")
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause """Cannot choose between the following variants of org:foo:1.0:
  - api
  - runtime
All of them match the consumer attributes:
  - Variant 'api' capability org:foo:1.0:
      - Found custom 'v1' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
  - Variant 'runtime' capability org:foo:1.0:
      - Found custom 'v2' but wasn't required.
      - Found org.gradle.status 'release' but wasn't required.
      - Required org.gradle.usage 'java-api' but no value provided.
This error might be fixed by applying plugins understanding the 'another', 'dummy' and 'other' ecosystems."""
    }

}
