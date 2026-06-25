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

package org.gradle.internal.cc.impl.isolated

/**
 * Setting a default plugin version is meant to happen at settings time, via
 * {@code pluginManagement { plugins { ... } }}, before projects are loaded. Doing it afterwards
 * mutates build-scoped state that project configuration reads in parallel under Isolated Projects,
 * so it is reported as a violation. The internal {@code setDefaultPluginVersion} has no public
 * caller, so the build script reaches the build-scoped service directly to exercise the late call.
 */
class IsolatedProjectsPluginResolutionStrategyIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    private static final String SET_DEFAULT_VERSION_DURING_CONFIGURATION = '''
        def strategy = (gradle as org.gradle.api.internal.GradleInternal).services
            .get(org.gradle.plugin.management.internal.PluginResolutionStrategyInternal)
        strategy.setDefaultPluginVersion(org.gradle.plugin.use.internal.DefaultPluginId.of("com.example.foo"), "1.0")
    '''

    private static final String EXPECTED_VIOLATION = "Cannot set a default plugin version after projects have been loaded when Isolated Projects is enabled. The default version for plugin 'com.example.foo' was set too late."

    def "reports a violation when a default plugin version is set after projects are loaded"() {
        given:
        buildFile SET_DEFAULT_VERSION_DURING_CONFIGURATION

        when:
        isolatedProjectsFails("help")

        then:
        failure.assertHasCause(EXPECTED_VIOLATION)
    }

    def "the late call is allowed without Isolated Projects"() {
        given:
        buildFile SET_DEFAULT_VERSION_DURING_CONFIGURATION

        expect:
        succeeds("help")
    }

    def "reports a violation when a captured plugin management spec sets a version after projects are loaded"() {
        given:
        settingsFile '''
            def pluginManagementSpec = pluginManagement
            gradle.projectsLoaded {
                pluginManagementSpec.plugins.id("com.example.foo").version("1.0")
            }
        '''

        when:
        isolatedProjectsFails("help")

        then:
        // Thrown directly from the projectsLoaded callback, so the violation is the top-level
        // failure description rather than a nested cause.
        failure.assertHasDescription(EXPECTED_VIOLATION)
    }
}
