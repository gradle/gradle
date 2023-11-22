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
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.DependencyScopeConfiguration
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
import org.gradle.internal.artifacts.configurations.NoContextRoleBasedConfigurationCreationRequest
import org.gradle.internal.code.UserCodeApplicationContext
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
        !(legacy instanceof ResolvableConfiguration)
        !(legacy instanceof ConsumableConfiguration)
        !(legacy instanceof DependencyScopeConfiguration)
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
        verifyUnlocked(ConfigurationRoles.RESOLVABLE, "c") {
            resolvableUnlocked("c")
        }
        verifyUnlocked(ConfigurationRoles.RESOLVABLE, "d") {
            resolvableUnlocked("d", {})
        }
        verifyUnlocked(ConfigurationRoles.RESOLVABLE, "e") {
            maybeCreateResolvableUnlocked("e")
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
        verifyUnlocked(ConfigurationRoles.CONSUMABLE, "c") {
            consumableUnlocked("c")
        }
        verifyUnlocked(ConfigurationRoles.CONSUMABLE, "d") {
            consumableUnlocked("d", {})
        }
        verifyUnlocked(ConfigurationRoles.CONSUMABLE, "e") {
            maybeCreateConsumableUnlocked("e")
        }
    }

    def "creates dependency scope configuration"() {
        expect:
        verifyRole(ConfigurationRoles.DEPENDENCY_SCOPE, "a") {
            dependencyScope("a")
        }
        verifyRole(ConfigurationRoles.DEPENDENCY_SCOPE, "b") {
            dependencyScope("b", {})
        }
        verifyUnlocked(ConfigurationRoles.DEPENDENCY_SCOPE, "c") {
            dependencyScopeUnlocked("c")
        }
        verifyUnlocked(ConfigurationRoles.DEPENDENCY_SCOPE, "d") {
            dependencyScopeUnlocked("d", {})
        }
        verifyUnlocked(ConfigurationRoles.DEPENDENCY_SCOPE, "e") {
            maybeCreateDependencyScopeUnlocked("e")
        }
        verifyUnlocked(ConfigurationRoles.DEPENDENCY_SCOPE, "f") {
            maybeCreateDependencyScopeUnlocked("f", false)
        }
    }

    def "creates resolvable dependency scope configuration"() {
        expect:
        verifyUnlocked(ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, "a") {
            resolvableDependencyScopeUnlocked("a")
        }
        verifyUnlocked(ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, "b") {
            resolvableDependencyScopeUnlocked("b", {})
        }
        verifyUnlocked(ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, "c") {
            maybeCreateResolvableDependencyScopeUnlocked("c")
        }
    }

    def "can create migrating configurations"() {
        expect:
        verifyUnlocked(role, "a") {
            migratingUnlocked("a", role)
        }
        verifyUnlocked(role, "b") {
            migratingUnlocked("b", role) {}
        }
        verifyUnlocked(role, "c") {
            maybeCreateMigratingUnlocked("c", role)
        }

        where:
        role << [
            ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE,
            ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE,
            ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE,
            ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE,
            ConfigurationRolesForMigration.CONSUMABLE_TO_REMOVED
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
        configurationContainer.maybeCreateMigratingUnlocked("baz", role)

        then:
        thrown(InvalidUserDataException)

        where:
        role << [
            ConfigurationRoles.LEGACY,
            ConfigurationRoles.RESOLVABLE,
            ConfigurationRoles.CONSUMABLE,
            ConfigurationRoles.CONSUMABLE_DEPENDENCY_SCOPE,
            ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE
        ]
    }

    def "#name calls configure action with new configuration for lazy methods"() {
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
        name                              | action
        "consumable(String, Action)"      | { consumable("foo", it) }
        "resolvable(String, Action)"      | { resolvable("foo", it) }
        "dependencyScope(String, Action)" | { dependencyScope("foo", it) }
    }

    def "#name calls configure action with new configuration for eager methods"() {
        when:
        action.delegate = configurationContainer
        def arg = null
        def del = null
        def value = action({
            arg = it
            del = delegate
        })

        then:
        arg == value
        del == value

        where:
        name                                                | action
        "consumableUnlocked(String, Action)"                | { consumableUnlocked("foo", it) }
        "resolvableUnlocked(String, Action)"                | { resolvableUnlocked("foo", it) }
        "dependencyScopeUnlocked(String, Action)"           | { dependencyScopeUnlocked("foo", it) }
        "resolvableDependencyScopeUnlocked(String, Action)" | { resolvableDependencyScopeUnlocked("foo", it) }
    }

    def "role locked configurations default to non-visible"() {
        expect:
        !configurationContainer.consumable("a").get().visible
        !configurationContainer.consumable("b", {}).get().visible
        !configurationContainer.resolvable("c").get().visible
        !configurationContainer.resolvable("d", {}).get().visible
        !configurationContainer.dependencyScope("e").get().visible
        !configurationContainer.dependencyScope("f", {}).get().visible
    }

    def "cannot maybeCreate invalid role (#role)"() {
        when:
        configurationContainer.maybeCreate(new NoContextRoleBasedConfigurationCreationRequest("foo", role));

        then:
        def e = thrown(GradleException)
        e.message == "Cannot maybe create invalid role: ${role.getName()}"

        where:
        role << [ConfigurationRoles.LEGACY, ConfigurationRoles.CONSUMABLE_DEPENDENCY_SCOPE, ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE]
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
        verifyLazyConfiguration(name, producer) {
            assert role.resolvable == it instanceof ResolvableConfiguration
            assert role.declarable == it instanceof DependencyScopeConfiguration
            assert role.consumable == it instanceof ConsumableConfiguration
            assert role.resolvable == it.isCanBeResolved()
            assert role.declarable == it.isCanBeDeclared()
            assert role.consumable == it.isCanBeConsumed()
        }
    }

    def verifyUnlocked(ConfigurationRole role, String name, @DelegatesTo(ConfigurationContainerInternal) Closure producer) {
        verifyEagerConfiguration(name, producer) {
            assert !(it instanceof ResolvableConfiguration)
            assert !(it instanceof DependencyScopeConfiguration)
            assert !(it instanceof ConsumableConfiguration)
            assert role.resolvable == it.isCanBeResolved()
            assert role.declarable == it.isCanBeDeclared()
            assert role.consumable == it.isCanBeConsumed()
        }
    }

    def verifyEagerConfiguration(String name, @DelegatesTo(ConfigurationContainerInternal) Closure producer, Closure action) {
        producer.delegate = configurationContainer
        def value = producer()

        assert value.name == name

        def value2 = configurationContainer.getByName(name)

        assert value2.name == name

        action(value)
        action(value2)

        true
    }

    def verifyLazyConfiguration(String name, @DelegatesTo(ConfigurationContainerInternal) Closure producer, Closure action) {
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
