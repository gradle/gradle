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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

/**
 * These test cases document the current behavior when dependency constraints are combined
 * with other dependency management mechanisms. They do not represent recommended use cases.
 * If dependency constraints and component metadata rules are used, using other mechanisms
 * should not be required.
 */
class DependencyRuleAffectingConflictResolutionTest extends AbstractIntegrationSpec {
    private final ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                conf
            }
        """
    }

    /**
     * Real graph from one of our projects:
     *
     * +--- project :base
     * |    \--- com.linkedin.android-data-manager:DataManager:20.0.4 -> 20.0.6
     * |         \--- com.fasterxml.jackson.core:jackson-core:2.9.2 -> 2.8.3
     * +--- project :models
     * |    \--- com.linkedin.pegasus-android:pegasus:16.0.0
     * |         +--- com.fasterxml.jackson.core:jackson-core:2.8.3
     * |         \--- com.linkedin.android-data-manager:DataManager:20.0.6 (*)
     * \--- project :data:infra:infra-repo
     *      +--- project :base (*)
     *      \--- com.linkedin.android-li-payments:paymentsLibrary:4.0.1
     *           +--- com.fasterxml.jackson.core:jackson-core:2.8.2 -> 2.8.3
     *           \--- com.linkedin.android-data-manager:DataManager:14.0.2 -> 20.0.6 (*)
     */
    void "reproduce dependency rule bug"() {
        def jacksoncore292 = mavenRepo.module("org", "jackson-core", '2.9.2').publish()
        def datamanager204 = mavenRepo.module("org", "DataManager", '20.0.4')
            .dependsOn(jacksoncore292).publish()
        def base = mavenRepo.module("org", "base", '1.0')
            .dependsOn(datamanager204).publish()

        def jacksoncore283 = mavenRepo.module("org", "jackson-core", '2.8.3').publish()
        def datamanager206 = mavenRepo.module("org", "DataManager", '20.0.6')
            .dependsOn(jacksoncore292).publish()
        def pegasus = mavenRepo.module("org", "pegasus", '16.0.0')
            .dependsOn(jacksoncore283).dependsOn(datamanager206).publish()
        mavenRepo.module("org", "models", '1.0')
            .dependsOn(pegasus).publish()

        def datamanager142 = mavenRepo.module("org", "DataManager", '14.0.2').publish()
        def jacksoncore282 = mavenRepo.module("org", "jackson-core", '2.8.2').publish()
        def paymentsLibrary = mavenRepo.module("org", "paymentsLibrary", '4.0.1')
            .dependsOn(jacksoncore282).dependsOn(datamanager142).publish()

        mavenRepo.module("org", "infra-repo", '1.0')
            .dependsOn(base).dependsOn(paymentsLibrary).publish()

        buildFile << """
            dependencies {
                conf 'org:base:1.0'
                conf 'org:models:1.0'
                conf 'org:infra-repo:1.0'
            }
            configurations.conf.resolutionStrategy {
                eachDependency { DependencyResolveDetails details ->
                    if (details.requested.name == 'jackson-core' && details.requested.version == '2.8.2') {
                        details.useVersion '2.8.3'
                    }
                }
            }
        """

        expect:
        def result = run 'dependencies'

        //jackson-core should not be downgraded
        !result.output.contains("org:jackson-core:2.9.2 -> 2.8.3")
    }
}
