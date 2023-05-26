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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.DependenciesConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
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
import org.gradle.internal.work.WorkerThreadRegistry
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultConfigurationContainerTest extends Specification {

    private ConfigurationResolver resolver = Mock(ConfigurationResolver)
    private ListenerManager listenerManager = Stub(ListenerManager.class) {
        _ * getBroadcaster(ProjectDependencyObservedListener.class) >> Stub(ProjectDependencyObservedListener)
    }
    private DependencyMetaDataProvider metaDataProvider = Mock(DependencyMetaDataProvider.class)
    private ComponentIdentifierFactory componentIdentifierFactory = Mock(ComponentIdentifierFactory)
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
        callbackActionDecorator,
        rootComponentMetadataBuilderFactory,
        configurationFactory,
        Mock(ResolutionStrategyFactory)
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

    def "#name creates legacy configurations"() {
        when:
        action.delegate = configurationContainer
        def legacy = action()

        then:
        legacy instanceof ResolvableConfiguration
        legacy instanceof ConsumableConfiguration
        legacy instanceof DependenciesConfiguration
        legacy.isCanBeResolved()
        legacy.isCanBeConsumed()
        legacy.isCanBeDeclared()

        where:
        name                        | action
        "create(String)"            | { create("foo") }
        "maybeCreate(String)"       | { maybeCreate("foo") }
        "create(String, Action)"    | { create("foo") {} }
        "register(String)"          | { register("foo").get() }
        "register(String, Action)"  | { register("foo", {}).get() }
    }

    def "creates resolvable configurations"() {
        expect:
        verifyRole(ConfigurationRoles.RESOLVABLE, "a") {
            resolvable("a")
        }
        verifyRole(ConfigurationRoles.RESOLVABLE, "b") {
            resolvable("b", {})
        }
        verifyRole(ConfigurationRoles.RESOLVABLE, "c") {
            resolvableUnlocked("c")
        }
        verifyRole(ConfigurationRoles.RESOLVABLE, "d") {
            resolvableUnlocked("d", {})
        }
        verifyRole(ConfigurationRoles.RESOLVABLE, "e") {
            maybeRegisterResolvableUnlocked("e", {})
        }
    }

    def "creates consumable configurations"() {
        expect:
        verifyRole(ConfigurationRoles.CONSUMABLE, "a") {
            consumable("a")
        }
        verifyRole(ConfigurationRoles.CONSUMABLE, "b") {
            consumable("b", {})
        }
        verifyRole(ConfigurationRoles.CONSUMABLE, "c") {
            consumableUnlocked("c")
        }
        verifyRole(ConfigurationRoles.CONSUMABLE, "d") {
            consumableUnlocked("d", {})
        }
        verifyRole(ConfigurationRoles.CONSUMABLE, "e") {
            maybeRegisterConsumableUnlocked("e", {})
        }
    }

    def "#name creates dependencies configuration"() {
        expect:
        verifyRole(ConfigurationRoles.BUCKET, "a") {
            dependencies("a")
        }
        verifyRole(ConfigurationRoles.BUCKET, "b") {
            dependencies("b", {})
        }
        verifyRole(ConfigurationRoles.BUCKET, "c") {
            dependenciesUnlocked("c")
        }
        verifyRole(ConfigurationRoles.BUCKET, "d") {
            dependenciesUnlocked("d", {})
        }
        verifyRole(ConfigurationRoles.BUCKET, "e") {
            maybeRegisterDependenciesUnlocked("e", {})
        }
        verifyRole(ConfigurationRoles.BUCKET, "f") {
            maybeRegisterDependenciesUnlocked("f", false, {})
        }
    }

    def "#name creates resolvable dependencies configuration"() {
        expect:
        verifyRole(ConfigurationRoles.RESOLVABLE_BUCKET, "a") {
            resolvableDependenciesUnlocked("a")
        }
        verifyRole(ConfigurationRoles.RESOLVABLE_BUCKET, "b") {
            resolvableDependenciesUnlocked("b", {})
        }
        verifyRole(ConfigurationRoles.RESOLVABLE_BUCKET, "c") {
            maybeRegisterResolvableDependenciesUnlocked("c", {})
        }
    }

    def "can create migrating configurations"() {
        expect:
        verifyRole(role, "a") {
            migratingUnlocked("a", role)
        }
        verifyRole(role, "b") {
            migratingUnlocked("b", role) {}
        }
        verifyRole(role, "c") {
            maybeRegisterMigratingUnlocked("c", role) {}
        }

        where:
        role << [
            ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_BUCKET,
            ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE,
            ConfigurationRolesForMigration.RESOLVABLE_BUCKET_TO_RESOLVABLE,
            ConfigurationRolesForMigration.CONSUMABLE_BUCKET_TO_CONSUMABLE,
        ]
    }

    def "cannot create arbitrary roles with migrating factory methods"() {
        when:
        configurationContainer.migratingUnlocked("foo", role)

        then:
        thrown(InvalidUserDataException)

        when:
        configurationContainer.migratingUnlocked("bar", role) {}

        then:
        thrown(InvalidUserDataException)

        when:
        configurationContainer.maybeRegisterMigratingUnlocked("baz", role) {}

        then:
        thrown(InvalidUserDataException)

        where:
        role << [
            ConfigurationRoles.LEGACY,
            ConfigurationRoles.RESOLVABLE,
            ConfigurationRoles.CONSUMABLE,
            ConfigurationRoles.CONSUMABLE_BUCKET,
            ConfigurationRoles.RESOLVABLE_BUCKET
        ]
    }

    def "#name calls configure action with new configuration"() {
        when:
        action.delegate = configurationContainer
        def arg = null
        def del = null
        def value = action({
            arg = it
            del = delegate
        }).get()

        then:
        arg == value
        del == value

        where:
        name                                                           | action
        "consumable(String, Action)"                                   | { consumable("foo", it) }
        "resolvable(String, Action)"                                   | { resolvable("foo", it) }
        "dependencies(String, Action)"                                 | { dependencies("foo", it) }
        "consumableUnlocked(String, Action)"                           | { consumableUnlocked("foo", it) }
        "resolvableUnlocked(String, Action)"                           | { resolvableUnlocked("foo", it) }
        "dependenciesUnlocked(String, Action)"                         | { dependenciesUnlocked("foo", it) }
        "resolvableDependenciesUnlocked(String, Action)"               | { resolvableDependenciesUnlocked("foo", it) }
        "maybeRegisterConsumableUnlocked(String, Action)"              | { maybeRegisterConsumableUnlocked("foo", it) }
        "maybeRegisterResolvableUnlocked(String, Action)"              | { maybeRegisterResolvableUnlocked("foo", it) }
        "maybeRegisterDependenciesUnlocked(String, Action)"            | { maybeRegisterDependenciesUnlocked("foo", it) }
        "maybeRegisterResolvableDependenciesUnlocked(String, Action)"  | { maybeRegisterResolvableDependenciesUnlocked("foo", it) }
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

    def verifyRole(ConfigurationRole role, String name, @DelegatesTo(ConfigurationContainerInternal) Closure producer) {
        def action = {
            assert role.resolvable == it instanceof ResolvableConfiguration
            assert role.declarable == it instanceof DependenciesConfiguration
            assert role.consumable == it instanceof ConsumableConfiguration
            assert role.resolvable == it.isCanBeResolved()
            assert role.declarable == it.isCanBeDeclared()
            assert role.consumable == it.isCanBeConsumed()
        }

        producer.delegate = configurationContainer
        def provider = producer()

        assert provider.isPresent()
        assert provider.name == name

        def provider2 = configurationContainer.named(name)

        assert provider2.isPresent()
        assert provider2.name == name

        def value = provider.get()

        action(value)

        value = provider2.get()

        action(value)

        true
    }
}
