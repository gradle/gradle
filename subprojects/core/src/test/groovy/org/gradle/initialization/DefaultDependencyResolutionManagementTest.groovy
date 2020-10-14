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

package org.gradle.initialization

import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.configuration.internal.UserCodeApplicationContext
import spock.lang.Specification
import spock.lang.Subject

class DefaultDependencyResolutionManagementTest extends Specification {
    def context = Mock(UserCodeApplicationContext)
    def services = Mock(DependencyManagementServices)
    def fileResolver = Mock(FileResolver)
    def fileCollectionFactory = Mock(FileCollectionFactory)
    def dependencyMetaDataProvider = Mock(DependencyMetaDataProvider)

    @Subject
    DefaultDependencyResolutionManagement drm = new DefaultDependencyResolutionManagement(context,
        services,
        fileResolver,
        fileCollectionFactory,
        dependencyMetaDataProvider)

    def "defaults to project repositories"() {
        expect:
        drm.repositoryMode == DependencyResolutionManagementInternal.RepositoryMode.PREFER_PROJECT

        when:
        drm.preferSettingsRepositories()

        then:
        drm.repositoryMode == DependencyResolutionManagementInternal.RepositoryMode.PREFER_SETTINGS

        when:
        drm.enforceSettingsRepositories()

        then:
        drm.repositoryMode == DependencyResolutionManagementInternal.RepositoryMode.FAIL_ON_PROJECT_REPOS

        when:
        drm.preferProjectRepositories()

        then:
        drm.repositoryMode == DependencyResolutionManagementInternal.RepositoryMode.PREFER_PROJECT
    }
}
