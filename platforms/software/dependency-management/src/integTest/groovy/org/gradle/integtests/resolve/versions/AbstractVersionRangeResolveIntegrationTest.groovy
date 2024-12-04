/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.resolve.versions

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.resolve.scenarios.VersionRangeResolveTestScenarios
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

/**
 * A comprehensive test of dependency resolution of a single module version, given a set of input selectors.
 * This integration test validates all scenarios in {@link VersionRangeResolveTestScenarios}, as well as some adhoc scenarios.
 */
@Requires(value = IntegTestPreconditions.IsEmbeddedExecutor, reason = ONLY_RUN_ON_EMBEDDED_REASON)
abstract class AbstractVersionRangeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    public static final String ONLY_RUN_ON_EMBEDDED_REASON = """
This test is very expensive. Ideally we shouldn't need an integration test here, but lack the
infrastructure to simulate everything done here, so we're only going to execute this test in
embedded mode
"""
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
                maven { url = '${mavenRepo.uri}' }
            }
            configurations {
                conf
            }
"""
        resolve.prepare()
        baseBuild = buildFile.text
        baseSettings = settingsFile.text
    }

    void checkScenarioResolution(String expectedSingle, String expectedMulti, VersionRangeResolveTestScenarios.RenderableVersion... versions) {
        checkScenarioResolution(expectedSingle, expectedMulti, versions as List)
    }

    void checkScenarioResolution(String expectedSingle, String expectedMulti, List<VersionRangeResolveTestScenarios.RenderableVersion> versions) {
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
            createDirs("p${i}")
            settingsFile << """
                include ':p${i}'
"""
        }

        boolean expectFailureSingle = expectedSingle == VersionRangeResolveTestScenarios.REJECTED || expectedSingle == VersionRangeResolveTestScenarios.FAILED
        boolean expectFailureMulti = expectedMulti == VersionRangeResolveTestScenarios.REJECTED || expectedMulti == VersionRangeResolveTestScenarios.FAILED
        if (expectFailureMulti) {
            fails 'resolveMultiProject'
        }
        if (expectFailureSingle) {
            fails 'resolveSingleProject'
        }

        if (!expectFailureMulti) {
            run 'resolveMultiProject'
            def multiProjectResolve = file('libs-multi').list() as List
            assert parseResolvedVersion(multiProjectResolve) == expectedMulti
        }

        if (!expectFailureSingle) {
            run 'resolveSingleProject'
            def singleProjectResolve = file('libs-single').list() as List
            assert parseResolvedVersion(singleProjectResolve) == expectedSingle
        }
    }

    def parseResolvedVersion(resolvedFiles) {
        assert resolvedFiles.size() == 1
        def resolvedFile = resolvedFiles.get(0)
        assert resolvedFile.startsWith('foo-')
        assert resolvedFile.endsWith('.jar')
        def resolvedVersion = (resolvedFile =~ /\d+/).getAt(0)
        resolvedVersion
    }
}
