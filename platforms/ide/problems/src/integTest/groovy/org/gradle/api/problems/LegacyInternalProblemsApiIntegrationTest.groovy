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

package org.gradle.api.problems

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.util.GradleVersion
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/38073")
class LegacyInternalProblemsApiIntegrationTest extends AbstractIntegrationSpec {

    def "a plugin injecting the removed internal Problems API fails with an actionable error"() {
        given:
        def pluginBuilder = new PluginBuilder(file("buildSrc"))
        pluginBuilder.java("LegacyService.java") << """
            package org.gradle.test;

            import ${BuildService.name};
            import ${BuildServiceParameters.name};
            import javax.inject.Inject;

            public abstract class LegacyService implements BuildService<BuildServiceParameters.None> {
                @Inject
                public LegacyService(org.gradle.api.problems.internal.InternalProblems problems) {
                }
            }
        """
        pluginBuilder.addPlugin("""
            project.gradle.sharedServices.registerIfAbsent("legacy", org.gradle.test.LegacyService) {}.get()
        """)
        pluginBuilder.generateForBuildSrc()

        buildFile << """
            plugins {
                id 'test-plugin'
            }
        """

        when:
        fails("help")

        then:
        failureCauseContains(
            "Plugin 'test-plugin' relies on 'org.gradle.api.problems.internal.InternalProblems', " +
                "a Gradle internal API that was removed in Gradle 9.6.0. " +
                "Update the plugin to a version that no longer uses Gradle internal APIs, or use Gradle 9.5. " +
                "For more information, please refer to " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#agp_8x_incompatible " +
                "in the Gradle documentation."
        )
    }
}
