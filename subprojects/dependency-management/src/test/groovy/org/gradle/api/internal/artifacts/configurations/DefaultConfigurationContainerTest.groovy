/*
 * Copyright 2010 the original author or authors.
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

import groovy.test.NotYetImplemented
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.DependenciesConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.vcs.internal.VcsMappingsStore
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicReference

class DefaultConfigurationContainerTest extends Specification {

    private ConfigurationResolver resolver = Mock(ConfigurationResolver)
    private ListenerManager listenerManager = Stub(ListenerManager.class) {
        _ * getBroadcaster(ProjectDependencyObservedListener.class) >> Stub(ProjectDependencyObservedListener)
    }
    private DependencyMetaDataProvider metaDataProvider = Mock(DependencyMetaDataProvider.class)
    private ComponentIdentifierFactory componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    private DependencySubstitutionRules globalSubstitutionRules = Mock(DependencySubstitutionRules)
    private VcsMappingsStore vcsMappingsInternal = Mock(VcsMappingsStore)
    private BuildOperationExecutor buildOperationExecutor = Mock(BuildOperationExecutor)
    private DependencyLockingProvider lockingProvider = Mock(DependencyLockingProvider)
    private ProjectStateRegistry projectStateRegistry = Mock(ProjectStateRegistry)
    private CollectionCallbackActionDecorator callbackActionDecorator = Mock(CollectionCallbackActionDecorator) {
        decorate(_ as Action) >> { it[0] }
    }
    private UserCodeApplicationContext userCodeApplicationContext = Mock()
    private CalculatedValueContainerFactory calculatedValueContainerFactory = Mock()
    private Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()
    private ImmutableAttributesFactory immutableAttributesFactory = AttributeTestUtil.attributesFactory()
    private ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock() {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }
    private ComponentSelectorConverter componentSelectorConverter = Mock()
    private DomainObjectContext domainObjectContext = new RootScriptDomainObjectContext()
    private DefaultRootComponentMetadataBuilder metadataBuilder = Mock(DefaultRootComponentMetadataBuilder) {
        getValidator() >> Mock(MutationValidator)
    }
    private DefaultRootComponentMetadataBuilder.Factory rootComponentMetadataBuilderFactory = Mock(DefaultRootComponentMetadataBuilder.Factory) {
        create(_) >> metadataBuilder
    }
    private DefaultConfigurationFactory configurationFactory = new DefaultConfigurationFactory(
        instantiator,
        resolver,
        listenerManager,
        metaDataProvider,
        componentIdentifierFactory,
        lockingProvider,
        domainObjectContext,
        TestFiles.fileCollectionFactory(),
        buildOperationExecutor,
        new PublishArtifactNotationParserFactory(
                instantiator,
                metaDataProvider,
                TestFiles.resolver(),
                TestFiles.taskDependencyFactory(),
        ),
        immutableAttributesFactory,
        Stub(ResolveExceptionContextualizer),
        userCodeApplicationContext,
        projectStateRegistry,
        Mock(WorkerThreadRegistry),
        TestUtil.domainObjectCollectionFactory(),
        calculatedValueContainerFactory,
        TestFiles.taskDependencyFactory()
    )
    private DefaultConfigurationContainer configurationContainer = instantiator.newInstance(DefaultConfigurationContainer.class,
        instantiator,
        globalSubstitutionRules,
        vcsMappingsInternal,
        componentIdentifierFactory,
        immutableAttributesFactory,
        moduleIdentifierFactory,
        componentSelectorConverter,
        lockingProvider,
        callbackActionDecorator,
        Mock(NotationParser),
        TestUtil.objectFactory(),
        rootComponentMetadataBuilderFactory,
        configurationFactory
    )

    def addsNewConfigurationWhenConfiguringSelf() {
        when:
        configurationContainer.configure {
            newConf
        }

        then:
        configurationContainer.findByName('newConf') != null
        configurationContainer.newConf != null
    }

    def doesNotAddNewConfigurationWhenNotConfiguringSelf() {
        when:
        configurationContainer.getByName('unknown')

        then:
        thrown(UnknownConfigurationException)
    }

    def makesExistingConfigurationAvailableAsProperty() {
        when:
        Configuration configuration = configurationContainer.create('newConf')

        then:
        configuration != null
        configurationContainer.getByName("newConf").is(configuration)
        configurationContainer.newConf.is(configuration)
    }

    def addsNewConfigurationWithClosureWhenConfiguringSelf() {
        when:
        String someDesc = 'desc1'
        configurationContainer.configure {
            newConf {
                description = someDesc
            }
        }

        then:
        configurationContainer.newConf.getDescription() == someDesc
    }

    def makesExistingConfigurationAvailableAsConfigureMethod() {
        when:
        String someDesc = 'desc1'
        configurationContainer.create('newConf')
        Configuration configuration = configurationContainer.newConf {
            description = someDesc
        }

        then:
        configuration.getDescription() == someDesc
    }

    def makesExistingConfigurationAvailableAsConfigureMethodWhenConfiguringSelf() {
        when:
        String someDesc = 'desc1'
        Configuration configuration = configurationContainer.create('newConf')
        configurationContainer.configure {
            newConf {
                description = someDesc
            }
        }

        then:
        configuration.getDescription() == someDesc
    }

    def newConfigurationWithNonClosureParametersShouldThrowMissingMethodEx() {
        when:
        configurationContainer.newConf('a', 'b')

        then:
        thrown MissingMethodException
    }

    def "create(String) creates legacy configuration"() {
        when:
        def legacy = configurationContainer.create("foo")

        then:
        legacy instanceof ResolvableConfiguration
        legacy instanceof ConsumableConfiguration
        legacy instanceof DependenciesConfiguration
        legacy.isCanBeResolved()
        legacy.isCanBeConsumed()
        legacy.isCanBeDeclared()
    }

    def "create(String, Action) creates legacy configuration"() {
        when:
        AtomicReference<Configuration> receiver = new AtomicReference<>()
        def legacy = configurationContainer.create("foo", receiver::set)

        then:
        legacy == receiver.get()
        legacy instanceof ResolvableConfiguration
        legacy instanceof ConsumableConfiguration
        legacy instanceof DependenciesConfiguration
        legacy.isCanBeResolved()
        legacy.isCanBeConsumed()
        legacy.isCanBeDeclared()
    }

    def "resolvable(String) creates resolvable configuration"() {
        when:
        def resolvable = configurationContainer.resolvable("foo")

        then:
        resolvable instanceof ResolvableConfiguration
        !(resolvable instanceof ConsumableConfiguration)
        !(resolvable instanceof DependenciesConfiguration)
        resolvable.isCanBeResolved()
        !resolvable.isCanBeConsumed()
        !resolvable.isCanBeDeclared()
    }

    def "resolvable(String, Action) creates resolvable configuration"() {
        when:
        AtomicReference<ResolvableConfiguration> receiver = new AtomicReference<>()
        def resolvable = configurationContainer.resolvable("foo", receiver::set)

        then:
        resolvable == receiver.get()
        resolvable instanceof ResolvableConfiguration
        !(resolvable instanceof ConsumableConfiguration)
        !(resolvable instanceof DependenciesConfiguration)
        resolvable.isCanBeResolved()
        !resolvable.isCanBeConsumed()
        !resolvable.isCanBeDeclared()
    }

    def "consumable(String) creates consumable configuration"() {
        when:
        def consumable = configurationContainer.consumable("foo")

        then:
        consumable instanceof ConsumableConfiguration
        !(consumable instanceof ResolvableConfiguration)
        !(consumable instanceof DependenciesConfiguration)
        consumable.isCanBeConsumed()
        !consumable.isCanBeResolved()
        !consumable.isCanBeDeclared()
    }

    def "consumable(String, Action) creates consumable configuration"() {
        when:
        AtomicReference<ConsumableConfiguration> receiver = new AtomicReference<>()
        def consumable = configurationContainer.consumable("foo", receiver::set)

        then:
        consumable == receiver.get()
        consumable instanceof ConsumableConfiguration
        !(consumable instanceof ResolvableConfiguration)
        !(consumable instanceof DependenciesConfiguration)
        consumable.isCanBeConsumed()
        !consumable.isCanBeResolved()
        !consumable.isCanBeDeclared()
    }

    def "dependencies(String) creates dependencies configuration"() {
        when:
        def dependencies = configurationContainer.dependencies("foo")

        then:
        dependencies instanceof DependenciesConfiguration
        !(dependencies instanceof ConsumableConfiguration)
        !(dependencies instanceof ResolvableConfiguration)
        dependencies.isCanBeDeclared()
        !dependencies.isCanBeConsumed()
        !dependencies.isCanBeResolved()
    }

    def "dependencies(String, Action) creates dependencies configuration"() {
        when:
        AtomicReference<DependenciesConfiguration> receiver = new AtomicReference<>()
        def dependencies = configurationContainer.dependencies("foo", receiver::set)

        then:
        dependencies == receiver.get()
        dependencies instanceof DependenciesConfiguration
        !(dependencies instanceof ConsumableConfiguration)
        !(dependencies instanceof ResolvableConfiguration)
        dependencies.isCanBeDeclared()
        !dependencies.isCanBeConsumed()
        !dependencies.isCanBeResolved()
    }

    // withType when used with a class that is not a super-class of the container does not work with registered elements
    @NotYetImplemented
    def "can find all configurations even when they're registered"() {
        when:
        configurationContainer.register("foo")
        configurationContainer.create("bar")
        then:
        configurationContainer.withType(ConfigurationInternal).toList()*.name == ["bar", "foo"]
    }
}
