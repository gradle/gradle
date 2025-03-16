/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion
/**
 This test exists to ensure that the {@code @Deprecated} {@link Upload} task can still be registered
 and its properties accessed for backwards compatibility; but that it throws an exception upon usage.
 */
class UploadTaskIntegrationTest extends AbstractIntegrationSpec {
    def "deprecated Upload task class can be registered, but accessing properties are deprecated and fails at execution time"() {
        given:
        buildFile << """
            plugins {
                id 'java'
            }

            tasks.register('upload', Upload) {
                def ud = uploadDescriptor
                uploadDescriptor = true

                def dd = descriptorDestination
                descriptorDestination = file('descriptor.txt')

                def rh = repositories
                repositories {}

                def c = configuration
                configuration = configurations.getByName('compileClasspath')

                def a = artifacts
            }
        """

        expect:
        expectDeprecations()
        succeeds 'tasks'

        when:
        executer.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        expectDeprecations()
        fails('upload')

        then:
        result.assertHasErrorOutput "The legacy `Upload` task was removed in Gradle 8. Please use the `maven-publish` or `ivy-publish` plugin instead. " +
            "For more on publishing on maven repositories, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/publishing_maven.html#publishing_maven in the Gradle documentation."
    }

    def expectDeprecations() {
        executer.expectDocumentedDeprecationWarning("The Upload.isUploadDescriptor() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.setUploadDescriptor(boolean) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.getDescriptorDestination() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.setDescriptorDestination(File) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.getRepositories() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.repositories(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.getConfiguration() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.setConfiguration(Configuration) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
        executer.expectDocumentedDeprecationWarning("The Upload.getArtifacts() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
    }
}
