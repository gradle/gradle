/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class TaskActionIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {
    // When configuration cache is enabled, this is tested in ConfigurationCacheTaskExecutionIntegrationTest
    @UnsupportedWithConfigurationCache(because = "tests unsupported behaviour")
    def "nags when task action uses Task.project"() {
        buildFile """
            task broken {
                doLast {
                    project
                }
            }
        """

        when:
        expectTaskGetProjectDeprecations()
        succeeds("broken")

        then:
        noExceptionThrown()
    }

    // When configuration cache is enabled, this is tested in ConfigurationCacheTaskExecutionIntegrationTest
    @UnsupportedWithConfigurationCache(because = "tests unsupported behaviour")
    def "nags when task action uses #accessor"() {
        buildFile """
            task broken {
                doLast {
                    ${accessor}
                }
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Invocation of Task.${deprecatedProperty} at execution time has been deprecated. This will fail with an error in Gradle 10. This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#task_dependencies")
        succeeds("broken")

        then:
        noExceptionThrown()

        where:
        [accessor, deprecatedProperty] << [
                ["getTaskDependencies()", "taskDependencies"],
                ["getDependsOn()", "dependsOn"],
                ["getMustRunAfter()", "mustRunAfter"],
                ["getFinalizedBy()", "finalizedBy"],
                ["getShouldRunAfter()", "shouldRunAfter"],
        ]
    }

    // When configuration cache is enabled, this is tested in ConfigurationCacheTaskExecutionIntegrationTest
    @UnsupportedWithConfigurationCache(because = "tests unsupported behaviour")
    def "nags when task action uses Task.getExtensions()"() {
        buildFile """
            task broken {
                doLast {
                    extensions
                }
            }
        """

        when:
        expectTaskGetExtensionsDeprecations()
        succeeds("broken")

        then:
        noExceptionThrown()
    }

    // When configuration cache is enabled, this is tested in ConfigurationCacheUnsupportedTypesIntegrationTest
    @UnsupportedWithConfigurationCache(because = "tests unsupported behaviour")
    def "nags when task action accesses injected #serviceType service"() {
        buildFile """
            abstract class Foo extends DefaultTask {
                @javax.inject.Inject
                abstract ${serviceType} getInjected()

                @TaskAction
                void action() {
                    println(getInjected())
                }
            }

            tasks.register('foo', Foo)
        """

        when:
        executer.expectDocumentedDeprecationWarning("Reading injected service of type ${serviceType.tokenize('.').last()} at execution time has been deprecated. This will fail with an error in Gradle 10. This API is incompatible with the configuration cache, which will become the only mode supported by Gradle in a future release. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#injected_service_types_at_execution")
        succeeds("foo")

        then:
        noExceptionThrown()

        where:
        serviceType <<
            [
                "org.gradle.api.Project",
                "org.gradle.api.internal.project.ProjectInternal",
                "org.gradle.api.invocation.Gradle",
                "org.gradle.api.internal.GradleInternal",
            ]
    }
}
