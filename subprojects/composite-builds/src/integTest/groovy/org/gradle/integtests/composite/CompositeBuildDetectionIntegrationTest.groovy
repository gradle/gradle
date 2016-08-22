/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
/**
 * Tests for resolving dependency artifacts with substitution within a composite build.
 */
class CompositeBuildDetectionIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        dependency('org.test:buildB:1.0')
        buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildB
    }

    def "can detect composite build"() {
        when:
        buildA.buildFile << """
            import org.gradle.initialization.buildtype.BuildTypeAttributes
            def buildType = gradle.services.get(BuildTypeAttributes)

            assert buildType.compositeBuild
            assert !buildType.nestedBuild
"""

        then:
        execute(buildA, "jar")
    }

    def "included build is flagged as a nested build without composite parent on initial configuration"() {
        when:
        buildB.buildFile << """
            import org.gradle.initialization.buildtype.BuildTypeAttributes

            def buildType = gradle.services.get(BuildTypeAttributes)
            assert !buildType.compositeBuild
            assert buildType.nestedBuild

            def parentBuildType = gradle.parent.services.get(BuildTypeAttributes)
            assert !parentBuildType.compositeBuild
            assert !parentBuildType.nestedBuild
"""

        then:
        execute(buildA, "jar")
    }

    def "included build with declared substitutions is flagged as a nested build with composite parent"() {
        when:
        buildA.settingsFile << """
            includeBuild('${buildB.toURI()}') {
                dependencySubstitution {
                    substitute module('org.test:buildB:1.0') with project(':')
                }
            }
"""

        buildB.buildFile << """
            import org.gradle.initialization.buildtype.BuildTypeAttributes

            def buildType = gradle.services.get(BuildTypeAttributes)
            assert !buildType.compositeBuild
            assert buildType.nestedBuild

            def parentBuildType = gradle.parent.services.get(BuildTypeAttributes)
            assert parentBuildType.compositeBuild
            assert !parentBuildType.nestedBuild
"""

        then:
        execute(buildA, "jar")
    }
}
