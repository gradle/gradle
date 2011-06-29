/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.StartParameter
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.maven.MavenFactory
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.logging.LoggingManagerInternal
import spock.lang.Specification

class DefaultDependencyManagementServicesTest extends Specification {
    final ServiceRegistry parent = Mock()
    final FileResolver fileResolver = Mock()
    final DependencyMetaDataProvider dependencyMetaDataProvider = Mock()
    final ProjectFinder projectFinder = Mock()
    final ClassGenerator classGenerator = Mock()
    final DomainObjectContext domainObjectContext = Mock()
    final TestRepositoryHandler repositoryHandler = Mock()
    final ConfigurationContainer configurationContainer = Mock()
    final StartParameter startParameter = Mock()
    final DefaultDependencyManagementServices services = new DefaultDependencyManagementServices(parent)

    def "provides a ResolverFactory"() {
        given:
        _ * parent.getFactory(LoggingManagerInternal.class)

        expect:
        services.get(ResolverFactory.class) != null
    }

    def "provides a MavenFactory"() {
        expect:
        services.get(MavenFactory.class) != null
    }

    def "can create dependency resolution services"() {
        given:
        _ * parent.get(ClassGenerator.class) >> classGenerator
        _ * parent.get(StartParameter.class) >> startParameter
        1 * classGenerator.newInstance(DefaultRepositoryHandler.class, _, _, _) >> repositoryHandler
        1 * classGenerator.newInstance(DefaultConfigurationContainer.class, !null, classGenerator, domainObjectContext) >> configurationContainer

        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)

        then:
        resolutionServices.resolveRepositoryHandler != null
        resolutionServices.configurationContainer != null
        resolutionServices.dependencyHandler != null
        resolutionServices.publishServicesFactory != null
    }

    def "publish ResolverHandler shares ConventionMapping and ConfigurationContainer with resolve ResolverHandler"() {
        TestRepositoryHandler publishRepositoryHandler = Mock()

        given:
        _ * parent.get(StartParameter.class) >> startParameter
        _ * parent.get(ClassGenerator.class) >> classGenerator
        2 * classGenerator.newInstance(DefaultRepositoryHandler.class, _, _, _) >>> [repositoryHandler, publishRepositoryHandler]
        1 * classGenerator.newInstance(DefaultConfigurationContainer.class, !null, classGenerator, domainObjectContext) >> configurationContainer

        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)
        def publishResolverHandler = resolutionServices.publishServicesFactory.create().repositoryHandler

        then:
        publishResolverHandler == publishRepositoryHandler
        1 * publishRepositoryHandler.setConfigurationContainer(configurationContainer)
        1 * publishRepositoryHandler.setConventionMapping({!null})
    }

    def "publish services provide an IvyService"() {
        TestRepositoryHandler publishRepositoryHandler = Mock()

        given:
        _ * parent.get(StartParameter.class) >> startParameter
        _ * parent.get(ClassGenerator.class) >> classGenerator
        2 * classGenerator.newInstance(DefaultRepositoryHandler.class, _, _, _) >>> [repositoryHandler, publishRepositoryHandler]
        1 * classGenerator.newInstance(DefaultConfigurationContainer.class, !null, classGenerator, domainObjectContext) >> configurationContainer

        when:
        def resolutionServices = services.create(fileResolver, dependencyMetaDataProvider, projectFinder, domainObjectContext)
        def ivyService = resolutionServices.publishServicesFactory.create().ivyService

        then:
        ivyService != null
    }
}

abstract class TestRepositoryHandler extends DefaultRepositoryHandler implements IConventionAware {
    TestRepositoryHandler(ResolverFactory resolverFactory, FileResolver fileResolver, ClassGenerator classGenerator) {
        super(resolverFactory, fileResolver, classGenerator)
    }
}