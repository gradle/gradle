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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * A comprehensive test of dependency resolution of a single module version, given a set of input selectors.
 * This integration test validates all scenarios in {@link VersionRangeResolveTestScenarios}, as well as some adhoc scenarios.
 */
@IgnoreIf({
    // This test is very expensive. Ideally we shouldn't need an integration test here, but lack the
    // infrastructure to simulate everything done here, so we're only going to execute this test in
    // embedded mode
    !GradleContextualExecuter.embedded
})
class VersionRangeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def baseBuild
    def baseSettings
    def resolve = new ResolveTestFixture(buildFile, "conf").expectDefaultConfiguration("runtime")

    def setup() {
        (9..13).each {
            mavenRepo.module("org", "foo", "${it}").publish()
        }

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            configurations {
                conf
            }
"""
        resolve.prepare()
        baseBuild = buildFile.text
        baseSettings = settingsFile.text
    }

    @Unroll
    def "resolve pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        checkScenarioResolution(expected, candidates)

        where:
        permutation << VersionRangeResolveTestScenarios.SCENARIOS_TWO_DEPENDENCIES
    }

    @Unroll
    def "resolve prefer pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        checkScenarioResolution(expected, candidates)

        where:
        permutation << VersionRangeResolveTestScenarios.SCENARIOS_PREFER
    }

    @Unroll
    def "resolve reject pair #permutation"() {
        given:
        def candidates = permutation.candidates
        def expected = permutation.expected

        expect:
        checkScenarioResolution(expected, candidates)

        where:
        permutation << VersionRangeResolveTestScenarios.SCENARIOS_DEPENDENCY_WITH_REJECT
    }

    void checkScenarioResolution(String expected, VersionRangeResolveTestScenarios.RenderableVersion... versions) {
        checkScenarioResolution(expected, versions as List)
    }

    void checkScenarioResolution(String expected, List<VersionRangeResolveTestScenarios.RenderableVersion> versions) {
        settingsFile.text = baseSettings

        def singleProjectConfs = []
        def singleProjectDeps = []
        versions.eachWithIndex { VersionRangeResolveTestScenarios.RenderableVersion version, int i ->
            singleProjectConfs << "single${i}"
            singleProjectDeps << "single${i} " + version.render()
        }

        buildFile.text = baseBuild + """
            allprojects {
                configurations { conf }
            }
            
            configurations {
                ${singleProjectConfs.join('\n')}
                single {
                    extendsFrom(${singleProjectConfs.join(',')})
                }
            }

            dependencies {
                conf 'org:foo'
                conf project(path: ':p1', configuration: 'conf')
                ${singleProjectDeps.join('\n')}
            }
            
            task resolveMultiProject(type: Sync) {
                from configurations.conf
                into 'libs-multi'
            }
            
            task resolveSingleProject(type: Sync) {
                from configurations.single
                into 'libs-single'
            }
"""
        for (int i = 1; i <= versions.size(); i++) {
            VersionRangeResolveTestScenarios.RenderableVersion version = versions.get(i - 1);
            def nextProjectDependency = i < versions.size() ? "conf project(path: ':p${i + 1}', configuration: 'conf')" : ""
            buildFile << """
                project('p${i}') {
                    dependencies {
                        conf ${version.render()}
                        ${nextProjectDependency}
                    }
                }
"""
            settingsFile << """
                include ':p${i}'
"""
        }

        boolean expectFailure = expected == VersionRangeResolveTestScenarios.REJECTED || expected == VersionRangeResolveTestScenarios.FAILED
        if (expectFailure) {
            fails 'resolveMultiProject'
            fails 'resolveSingleProject'
            return
        }

        run 'resolveMultiProject'
        def multiProjectResolve = file('libs-multi').list() as List

        run 'resolveSingleProject'
        def singleProjectResolve = file('libs-single').list() as List

        assert multiProjectResolve == singleProjectResolve
        assert parseResolvedVersion(multiProjectResolve) == expected
    }

    def parseResolvedVersion(resolvedFiles) {
        assert resolvedFiles.size() == 1
        def resolvedFile = resolvedFiles.get(0)
        assert resolvedFile.startsWith('foo-')
        assert resolvedFile.endsWith('.jar')
        def resolvedVersion = (resolvedFile =~ /\d\d/).getAt(0)
        resolvedVersion
    }
}
