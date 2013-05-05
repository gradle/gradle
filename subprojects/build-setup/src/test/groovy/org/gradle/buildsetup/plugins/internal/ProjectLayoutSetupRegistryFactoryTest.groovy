/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ServiceRegistryFactory
import spock.lang.Specification
import spock.lang.Unroll


class ProjectLayoutSetupRegistryFactoryTest extends Specification {

    ProjectLayoutSetupRegistryFactory projectLayoutSetupRegistry
    ServiceRegistryFactory serviceFactory
    DocumentationRegistry documentationRegistry
    DependencyManagementServices dependencyManagementServices
    MavenSettingsProvider mavenSettingsProvider

    def setup(){
        ProjectInternal projectInternal = Mock()
        serviceFactory = Mock()
        dependencyManagementServices = Mock()
        dependencyManagementServices.get(MavenSettingsProvider) >> mavenSettingsProvider
        projectLayoutSetupRegistry =  new ProjectLayoutSetupRegistryFactory(projectInternal)
        projectInternal.services >> serviceFactory
        serviceFactory.get(DocumentationRegistry) >> documentationRegistry
        serviceFactory.get(DependencyManagementServices) >> dependencyManagementServices
    }

    @Unroll
    def "supports '#type' project descriptor type"(){
        when:
        ProjectSetupDescriptor descriptor = projectLayoutSetupRegistry.createProjectLayoutSetupRegistry().get(type)

        then:
        descriptor != null
        descriptor.class == clazz

        where:
        type             |   clazz
        "pom"            |   PomProjectSetupDescriptor.class
        "empty"          |   EmptyProjectSetupDescriptor.class
        "java-library"   |   JavaLibraryProjectSetupDescriptor.class
    }
}
