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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.Action
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.ResolveExceptionMapper
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.specs.Spec
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultConfigurationContainerSpec extends Specification {

    private ConfigurationResolver resolver = Mock()
    private Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private DomainObjectContext domainObjectContext = Mock()
    private ListenerManager listenerManager = Mock()
    private DependencyMetaDataProvider metaDataProvider = Mock()
    private FileCollectionFactory fileCollectionFactory = Mock()
    private BuildOperationRunner buildOperationRunner = Mock()
    private ProjectStateRegistry projectStateRegistry = Mock()
    private UserCodeApplicationContext userCodeApplicationContext = Mock()
    private CalculatedValueContainerFactory calculatedValueContainerFactory = Mock()

    private CollectionCallbackActionDecorator domainObjectCollectionCallbackActionDecorator = Mock(CollectionCallbackActionDecorator) {
        decorateSpec(_) >> { Spec spec -> spec }
        decorate(_ as Action) >> { it[0] }
    }
    def attributesFactory = AttributeTestUtil.attributesFactory()
    def metadataBuilder = Mock(DefaultRootComponentMetadataBuilder) {
        getValidator() >> Mock(MutationValidator)
    }
    private DefaultRootComponentMetadataBuilder.Factory rootComponentMetadataBuilderFactory = Mock(DefaultRootComponentMetadataBuilder.Factory) {
        create(_, _, _, _) >> metadataBuilder
    }
    private DefaultConfigurationFactory configurationFactory = new DefaultConfigurationFactory(
        instantiator,
        resolver,
        listenerManager,
        domainObjectContext,
        fileCollectionFactory,
        buildOperationRunner,
        Stub(PublishArtifactNotationParserFactory),
        attributesFactory,
        Stub(ResolveExceptionMapper),
        new AttributeDesugaring(AttributeTestUtil.attributesFactory()),
        userCodeApplicationContext,
        projectStateRegistry,
        Mock(WorkerThreadRegistry),
        TestUtil.domainObjectCollectionFactory(),
        calculatedValueContainerFactory,
        TestFiles.taskDependencyFactory(),
        TestUtil.problemsService()
    )
    private DefaultConfigurationContainer configurationContainer = new DefaultConfigurationContainer(
        instantiator,
        domainObjectCollectionCallbackActionDecorator,
        metaDataProvider,
        domainObjectContext,
        Mock(AttributesSchemaInternal),
        rootComponentMetadataBuilderFactory,
        configurationFactory,
        Mock(ResolutionStrategyFactory)
    )

    def setup() {
        metadataBuilder.newBuilder(_, _) >> metadataBuilder
    }

    def "adds and gets"() {
        1 * domainObjectContext.identityPath("compile") >> Path.path(":build:compile")
        1 * domainObjectContext.projectPath("compile") >> Path.path(":compile")
        1 * domainObjectContext.model >> StandaloneDomainObjectContext.ANONYMOUS

        when:
        def compile = configurationContainer.create("compile")

        then:
        compile.name == "compile"
        compile.incoming.path == ":compile"
        compile instanceof DefaultConfiguration

        and:
        configurationContainer.getByName("compile") == compile

        //finds configurations
        configurationContainer.findByName("compile") == compile
        configurationContainer.findByName("foo") == null
        configurationContainer.findAll { it.name == "compile" } as Set == [compile] as Set
        configurationContainer.findAll { it.name == "foo" } as Set == [] as Set

        configurationContainer as List == [compile] as List

        when:
        configurationContainer.getByName("fooo")

        then:
        thrown(UnknownConfigurationException)
    }

    def "configures and finds"() {
        1 * domainObjectContext.identityPath("compile") >> Path.path(":build:compile")
        1 * domainObjectContext.projectPath("compile") >> Path.path(":compile")
        1 * domainObjectContext.model >> StandaloneDomainObjectContext.ANONYMOUS

        when:
        def compile = configurationContainer.create("compile") {
            description = "I compile!"
        }

        then:
        configurationContainer.getByName("compile") == compile
        compile.description == "I compile!"
        compile.incoming.path == ":compile"
    }

    def "creates detached"() {
        given:
        1 * domainObjectContext.projectPath("detachedConfiguration1") >> Path.path(":detachedConfiguration1")
        1 * domainObjectContext.model >> StandaloneDomainObjectContext.ANONYMOUS

        def dependency1 = new DefaultExternalModuleDependency("group", "name", "version")
        def dependency2 = new DefaultExternalModuleDependency("group", "name2", "version")

        when:
        def detached = configurationContainer.detachedConfiguration(dependency1, dependency2)

        then:
        detached.name == "detachedConfiguration1"
        detached.getAll() == [detached] as Set
        detached.getHierarchy() == [detached] as Set
        [dependency1, dependency2].each { detached.getDependencies().contains(it) }
        detached.getDependencies().size() == 2
        detached.incoming.path == ":detachedConfiguration1"
    }
}
