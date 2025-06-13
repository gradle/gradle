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

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.ResolveExceptionMapper
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Specification

class DefaultConfigurationContainerTest extends Specification {

    private ConfigurationResolver resolver = Mock(ConfigurationResolver)
    private ConfigurationResolver.Factory resolverFactory = Mock(ConfigurationResolver.Factory) {
        create(_, _, _) >> resolver
    }

    private ListenerManager listenerManager = Stub(ListenerManager.class)
    private DependencyMetaDataProvider metaDataProvider = Mock(DependencyMetaDataProvider.class)
    private BuildOperationRunner buildOperationRunner = Mock(BuildOperationRunner)
    private ProjectStateRegistry projectStateRegistry = Mock(ProjectStateRegistry)
    private CollectionCallbackActionDecorator callbackActionDecorator = Mock(CollectionCallbackActionDecorator) {
        decorateSpec(_) >> { Spec spec -> spec }
        decorate(_ as Action) >> { it[0] }
    }
    private UserCodeApplicationContext userCodeApplicationContext = Mock()
    private CalculatedValueContainerFactory calculatedValueContainerFactory = Mock()
    private ObjectFactory objectFactory = TestUtil.objectFactory()
    private AttributesFactory attributesFactory = AttributeTestUtil.attributesFactory()
    private DefaultConfigurationFactory configurationFactory = new DefaultConfigurationFactory(
        objectFactory,
        listenerManager,
        StandaloneDomainObjectContext.ANONYMOUS,
        TestFiles.fileCollectionFactory(),
        buildOperationRunner,
        new PublishArtifactNotationParserFactory(
                objectFactory,
                metaDataProvider,
                TestFiles.resolver(),
                TestFiles.taskDependencyFactory(),
        ),
        attributesFactory,
        Stub(ResolveExceptionMapper),
        new AttributeDesugaring(AttributeTestUtil.attributesFactory()),
        userCodeApplicationContext,
        CollectionCallbackActionDecorator.NOOP,
        projectStateRegistry,
        TestUtil.domainObjectCollectionFactory(),
        calculatedValueContainerFactory,
        TestFiles.taskDependencyFactory(),
        TestUtil.problemsService(),
        new DocumentationRegistry()
    )

    private DefaultConfigurationContainer configurationContainer = objectFactory.newInstance(DefaultConfigurationContainer.class,
        TestUtil.instantiatorFactory().decorateLenient(),
        callbackActionDecorator,
        StandaloneDomainObjectContext.ANONYMOUS,
        configurationFactory,
        Mock(ResolutionStrategyFactory),
        TestUtil.problemsService(),
        resolverFactory,
        AttributeTestUtil.mutableSchema()
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
        verifyLocked(ConfigurationRoles.RESOLVABLE, "c") {
            resolvableLocked("c")
        }
        verifyLocked(ConfigurationRoles.RESOLVABLE, "d") {
            resolvableLocked("d", {})
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
        verifyLocked(ConfigurationRoles.CONSUMABLE, "c") {
            consumableLocked("c")
        }
        verifyLocked(ConfigurationRoles.CONSUMABLE, "d") {
            consumableLocked("d", {})
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
        verifyLocked(ConfigurationRoles.DEPENDENCY_SCOPE, "c") {
            dependencyScopeLocked("c")
        }
        verifyLocked(ConfigurationRoles.DEPENDENCY_SCOPE, "d") {
            dependencyScopeLocked("d", {})
        }
        verifyLocked(ConfigurationRoles.DEPENDENCY_SCOPE, "e") {
            maybeCreateDependencyScopeLocked("e", false)
        }
    }

    def "creates resolvable dependency scope configuration"() {
        expect:
        verifyLocked(ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, "a") {
            resolvableDependencyScopeLocked("a")
        }
        verifyLocked(ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, "b") {
            resolvableDependencyScopeLocked("b", {})
        }
    }

    def "can create migrating configurations"() {
        expect:
        verifyLocked(role, "a") {
            migratingLocked("a", role)
        }

        where:
        role << [
            ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_RESOLVABLE,
            ConfigurationRolesForMigration.RESOLVABLE_DEPENDENCY_SCOPE_TO_DEPENDENCY_SCOPE,
            ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE
        ]
    }

    def "cannot create arbitrary roles with migrating factory methods"() {
        when:
        configurationContainer.migratingLocked("foo", role)

        then:
        thrown(InvalidUserDataException)

        when:
        configurationContainer.migratingLocked("bar", role) {}

        then:
        thrown(InvalidUserDataException)

        where:
        role << [
            ConfigurationRoles.ALL,
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
        "consumableLocked(String, Action)"                | { consumableLocked("foo", it) }
        "resolvableLocked(String, Action)"                | { resolvableLocked("foo", it) }
        "dependencyScopeLocked(String, Action)"           | { dependencyScopeLocked("foo", it) }
        "resolvableDependencyScopeLocked(String, Action)" | { resolvableDependencyScopeLocked("foo", it) }
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

    // withType when used with a class that is not a super-class of the container does not work with registered elements
    @ToBeImplemented
    def "can find all configurations even when they're registered"() {
        when:
        configurationContainer.register("foo")
        configurationContainer.create("bar")
        then:
        configurationContainer.withType(ConfigurationInternal).toList()*.name == ["bar"] // This should include "foo" too, but it doesn't yet
    }

    def "can't #addMethod a #description to a configuration container"() {
        when:
        configurationContainer."$addMethod"(addMe)

        then:
        def e = thrown(GradleException)
        e.message == "Adding a $description directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container."

        where:
        description                         | addMethod     | addMe
        "configuration"                     | "add"         | Mock(Configuration.class)
        "configuration provider"            | "addLater"    | Mock(Provider.class)
        "collection of configurations"      | "addAll"      | [Mock(Configuration.class), Mock(Configuration.class)]
        "provider of configurations"        | "addAllLater" | Mock(Provider.class)
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

    def verifyLocked(ConfigurationRole role, String name, @DelegatesTo(ConfigurationContainerInternal) Closure producer) {
        verifyEagerConfiguration(name, producer) {
            assert !(it instanceof ResolvableConfiguration)
            assert !(it instanceof DependencyScopeConfiguration)
            assert !(it instanceof ConsumableConfiguration)
            assert role.resolvable == it.isCanBeResolved()
            assert role.declarable == it.isCanBeDeclared()
            assert role.consumable == it.isCanBeConsumed()

            def conf = it
            verifyUsageChangeFailsProperly { conf.canBeConsumed = !conf.canBeConsumed }
            verifyUsageChangeFailsProperly { conf.canBeResolved = !conf.canBeResolved }
            verifyUsageChangeFailsProperly { conf.canBeDeclared = !conf.canBeDeclared }
        }
    }

    private static verifyUsageChangeFailsProperly(Closure step) {
        try {
            step.call()
            assert false : "Expected exception to be thrown"
        } catch (GradleException e) {
            assert e.message.startsWith("Cannot change the allowed usage of configuration")
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
        Provider<?> provider = producer()

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
