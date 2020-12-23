/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class DefaultSourceDirectorySetFactoryTest extends AbstractIntegrationSpec {

    @Unroll
    def "shows deprecation warning when #deprecatedMethodCall is called"() {
        given:
        settingsFile << "rootProject.name = 'parent'"
        buildFile """
        apply plugin: org.test.TestPlugin

        task deprecation
        """
        file("buildSrc/src/main/java/org/test/TestPlugin.java") << """
            package org.test;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.Task;
            import org.gradle.api.internal.file.SourceDirectorySetFactory;

            import javax.inject.Inject;

            public class TestPlugin implements Plugin<Project> {

                private final SourceDirectorySetFactory sourceDirectorySetFactory;

                @Inject
                public TestPlugin(SourceDirectorySetFactory sourceDirectorySetFactory) {
                    this.sourceDirectorySetFactory = sourceDirectorySetFactory;
                }

                public void apply(Project project) {
                    sourceDirectorySetFactory.${deprecatedMethodCall};
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning('Internal API SourceDirectorySetFactory has been deprecated. This is scheduled to be removed in Gradle 7.0. ' +
            'Please use ObjectFactory.sourceDirectorySet(String, String) instead. ' +
            'See https://docs.gradle.org/current/userguide/lazy_configuration.html#property_files_api_reference for more details.')
        run 'deprecation'

        where:
        deprecatedMethodCall << ['create("name")', 'create("name", "displayName")']
    }
}
