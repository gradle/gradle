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

package org.gradle.internal.service

import com.google.common.reflect.TypeToken
import org.gradle.api.Action
import org.gradle.api.NonNullApi
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util.concurrent.Callable

class DefaultServiceRegistryTest extends Specification {
    TestRegistry registry = new TestRegistry()

    def throwsExceptionForUnknownService() {
        when:
        registry.get(StringBuilder.class)

        then:
        UnknownServiceException e = thrown()
        e.message == "No service of type StringBuilder available in TestRegistry."
    }

    def delegatesToParentForUnknownService() {
        def value = BigDecimal.TEN
        def parent = Mock(ParentServices)
        def registry = new TestRegistry(registry(parent))

        when:
        def result = registry.get(BigDecimal)

        then:
        result == value

        and:
        1 * parent.get(BigDecimal) >> value
    }

    def delegatesToParentsForUnknownService() {
        def value = BigDecimal.TEN
        def parent1 = Mock(ParentServices)
        def parent2 = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent1), registry(parent2))

        when:
        def result = registry.get(BigDecimal)

        then:
        result == value

        and:
        1 * parent1.get(BigDecimal) >> null
        1 * parent2.get(BigDecimal) >> value
    }

    def throwsExceptionForUnknownParentService() {
        def parent = Mock(ParentServices);
        def registry = new TestRegistry(registry(parent))

        given:
        _ * parent.get(StringBuilder) >> null

        when:
        registry.get(StringBuilder)

        then:
        UnknownServiceException e = thrown()
        e.message == "No service of type StringBuilder available in TestRegistry."
    }

    def returnsServiceInstanceThatHasBeenRegistered() {
        def value = BigDecimal.TEN
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(BigDecimal, value)

        expect:
        registry.get(BigDecimal) == value
        registry.get(Number) == value
    }

    def "does not support querying for Object.class"() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.get(Object)

        then:
        ServiceValidationException e = thrown()
        e.message == "Locating services with type Object is not supported."
    }

    def createsInstanceOfServiceImplementation() {
        def registry = new DefaultServiceRegistry()
        registry.register({ ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        } as Action)

        expect:
        registry.get(TestService) instanceof TestServiceImpl
        registry.get(TestService) == registry.get(TestServiceImpl)
    }

    def injectsServicesIntoServiceImplementation() {
        def registry = new DefaultServiceRegistry()
        registry.register({ ServiceRegistration registration ->
            registration.add(ServiceWithDependency)
            registration.add(TestServiceImpl)
        } as Action)

        expect:
        registry.get(ServiceWithDependency).service == registry.get(TestServiceImpl)
    }

    def usesFactoryMethodOnProviderToCreateServiceInstance() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new TestProvider())

        expect:
        registry.get(Integer) == 12
        registry.get(Number) == 12
    }

    def injectsServicesIntoProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new Object() {
            Integer createInteger() {
                return 12
            }

            String createString(Integer integer) {
                return integer.toString()
            }
        })

        expect:
        registry.get(String) == "12"
    }

    def injectsGenericTypesIntoProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new Object() {
            Integer createInteger(Factory<String> factory) {
                return factory.create().length()
            }

            Factory<String> createString(Callable<String> action) {
                return { action.call() } as Factory
            }

            Callable<String> createAction() {
                return { "hi" }
            }
        })

        expect:
        registry.get(Integer) == 2
    }

    def handlesInheritanceInGenericTypes() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new ProviderWithGenericTypes())

        expect:
        registry.get(Integer) == 123
    }

    def canHaveMultipleServicesWithParameterizedTypesAndSameRawType() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new Object() {
            Integer createInteger(Callable<Integer> factory) {
                return factory.call()
            }

            String createString(Callable<String> factory) {
                return factory.call()
            }

            Callable<Integer> createIntFactory() {
                return { 123 }
            }

            Callable<String> createStringFactory() {
                return { "hi" }
            }
        })

        expect:
        registry.get(Integer) == 123
        registry.get(String) == "hi"
    }

    def injectsParentServicesIntoProviderFactoryMethod() {
        def parent = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent))
        registry.addProvider(new Object() {
            String createString(Number n) {
                return n.toString()
            }
        })

        when:
        def result = registry.get(String)

        then:
        result == '123'

        and:
        1 * parent.get(Number) >> 123
    }

    def injectsGenericTypesFromParentIntoProviderFactoryMethod() {
        def parent = new DefaultServiceRegistry() {
            Callable<String> createStringCallable() {
                return { "hello" }
            }

            Factory<String> createStringFactory() {
                return { "world" } as Factory
            }
        }
        def registry = new DefaultServiceRegistry(parent)
        registry.addProvider(new Object() {
            String createString(Callable<String> callable, Factory<String> factory) {
                return callable.call() + ' ' + factory.create()
            }
        })

        expect:
        registry.get(String) == 'hello world'
    }

    def injectsServiceRegistryIntoProviderFactoryMethod() {
        def parent = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent))
        registry.addProvider(new Object() {
            String createString(ServiceRegistry services) {
                assert services.is(registry)
                return services.get(Number).toString()
            }
        })
        registry.add(Integer, 123)

        expect:
        registry.get(String) == '123'
    }

    def canLocateSelfAsAServiceOfTypeServiceRegistry() {
        def registry = new DefaultServiceRegistry()

        expect:
        registry.get(ServiceRegistry) == registry
        registry.find(DefaultServiceRegistry) == null
    }

    def failsWhenRegisteringAServiceOfTypeServiceRegistry() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.add(ServiceRegistry, new DefaultServiceRegistry())

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot define a service of type ServiceRegistry: Service ServiceRegistry with implementation DefaultServiceRegistry'
    }

    def failsWhenProviderFactoryMethodProducesAServiceOfTypeServiceRegistry() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new Object() {
            ServiceRegistry createServices() {
                return new DefaultServiceRegistry()
            }
        })

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot define a service of type ServiceRegistry: Service ServiceRegistry at DefaultServiceRegistryTest$.createServices()'
    }

    def failsWhenProviderFactoryMethodRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new StringProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type String using method DefaultServiceRegistryTest\$StringProvider.createString() as required service of type Runnable for parameter #1 is not available."

        when:
        registry.get(Number)

        then:
        e = thrown()
        e.message == "Cannot create service of type Integer using method DefaultServiceRegistryTest\$StringProvider.createInteger() as there is a problem with parameter #1 of type String."
        e.cause.message == "Cannot create service of type String using method DefaultServiceRegistryTest\$StringProvider.createString() as required service of type Runnable for parameter #1 is not available."
    }

    def failsWhenConstructorRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(RequiresService)
        }

        when:
        registry.get(RequiresService)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type DefaultServiceRegistryTest\$RequiresService using DefaultServiceRegistryTest\$RequiresService constructor as required service of type Number for parameter #1 is not available."
    }

    def failsWhenProviderFactoryMethodThrowsException() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new BrokenProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using BrokenProvider.createString()."
        e.cause == BrokenProvider.failure

        when:
        registry.get(Number)

        then:
        e = thrown()
        e.message == "Could not create service of type String using BrokenProvider.createString()."
        e.cause == BrokenProvider.failure
    }

    def failsWhenInterfaceIsRegistered() {
        def registry = new DefaultServiceRegistry()
        when:
        registry.register {
            it.add(Runnable)
        }

        then:
        def e = thrown(ServiceValidationException)
        e.message == "Cannot register an interface for construction."
    }

    def cachesInstancesCreatedUsingAProviderFactoryMethod() {
        def registry = new DefaultServiceRegistry()
        def provider = new Object() {
            String createString(Number number) {
                return number.toString()
            }

            Integer createInteger() {
                return 12
            }
        }
        registry.addProvider(provider)

        expect:
        registry.get(Integer).is(registry.get(Integer))
        registry.get(Number).is(registry.get(Number))

        and:
        registry.get(String).is(registry.get(String))
    }

    def usesProviderDecoratorMethodToDecorateParentServiceInstance() {
        def parent = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent))
        registry.addProvider(decoratorProvider)

        given:
        _ * parent.get(Long) >> 110L

        expect:
        registry.get(Long) == 112L
        registry.get(Number) == 112L

        where:
        decoratorProvider << [new TestDecoratingProviderWithCreate(), new TestDecoratingProviderWithDecorate()]
    }

    def cachesServiceCreatedUsingProviderDecoratorMethod() {
        def parent = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent))
        registry.addProvider(decoratorProvider)

        given:
        _ * parent.get(Long) >> 11L

        expect:
        registry.get(Long).is(registry.get(Long))

        where:
        decoratorProvider << [new TestDecoratingProviderWithCreate(), new TestDecoratingProviderWithDecorate()]
    }

    def conflictWhenCreateAndDecorateMethodDecorateTheSameType() {
        def parent = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent))
        registry.addProvider(new ConflictingDecoratorMethods())

        given:
        _ * parent.get(Long) >> 11L

        when:
        registry.get(Long).is(registry.get(Long))

        then:
        ServiceLookupException e = thrown()
        e.message.contains("Multiple services of type Long available in DefaultServiceRegistry:")
        e.message.contains('- Service Long at DefaultServiceRegistryTest$ConflictingDecoratorMethods.createLong()')
        e.message.contains('- Service Long at DefaultServiceRegistryTest$ConflictingDecoratorMethods.decorateLong()')

    }

    def providerDecoratorMethodFailsWhenNoParentRegistry() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(decoratorProvider)

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot use decorator method ${decoratorProvider.class.simpleName}.${methodName}Long() when no parent registry is provided."

        where:
        decoratorProvider                        | methodName
        new TestDecoratingProviderWithCreate()   | 'create'
        new TestDecoratingProviderWithDecorate() | 'decorate'
    }

    def failsWhenProviderDecoratorMethodRequiresUnknownService() {
        def parent = Mock(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent))

        given:
        registry.addProvider(decoratorProvider)

        when:
        registry.get(Long)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type Long using method DefaultServiceRegistryTest\$${decoratorProvider.class.simpleName}.${methodName}Long() as required service of type Long for parameter #1 is not available in parent registries."

        where:
        decoratorProvider                        | methodName
        new TestDecoratingProviderWithCreate()   | 'create'
        new TestDecoratingProviderWithDecorate() | 'decorate'
    }

    def failsWhenProviderDecoratorMethodThrowsException() {
        def parent = Stub(ParentServices) {
            get(Long) >> 12L
        }
        def registry = new DefaultServiceRegistry(registry(parent))

        given:
        registry.addProvider(decoratorProvider)

        when:
        registry.get(Long)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type Long using ${decoratorProvider.class.simpleName}.${methodName}Long()."
        e.cause == decoratorProvider.failure

        where:
        decoratorProvider                          | methodName
        new BrokenDecoratingProviderWithCreate()   | 'create'
        new BrokenDecoratingProviderWithDecorate() | 'decorate'
    }

    def failsWhenThereIsACycleInDependenciesForProviderFactoryMethods() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new ProviderWithCycle())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == 'Cannot create service of type String using method DefaultServiceRegistryTest$ProviderWithCycle.createString() as there is a problem with parameter #1 of type Integer.'
        e.cause.message == 'Cannot create service of type Integer using method DefaultServiceRegistryTest$ProviderWithCycle.createInteger() as there is a problem with parameter #1 of type String.'
        e.cause.cause.message == 'Cycle in dependencies of Service String at DefaultServiceRegistryTest$ProviderWithCycle.createString() detected'

        when:
        registry.getAll(Number)

        then:
        e = thrown()

        e.message == 'Cannot create service of type Integer using method DefaultServiceRegistryTest$ProviderWithCycle.createInteger() as there is a problem with parameter #1 of type String.'
        e.cause.message == 'Cannot create service of type String using method DefaultServiceRegistryTest$ProviderWithCycle.createString() as there is a problem with parameter #1 of type Integer.'
        e.cause.cause.message == 'Cycle in dependencies of Service Integer at DefaultServiceRegistryTest$ProviderWithCycle.createInteger() detected'
    }

    def failsWhenAProviderFactoryMethodReturnsNull() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new NullProvider())

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using NullProvider.createString() as this method returned null."
    }

    def failsWhenAProviderDecoratorCreateMethodReturnsNull() {
        def parent = Stub(ParentServices) {
            get(String) >> "parent"
        }
        def registry = new DefaultServiceRegistry(registry(parent))

        given:
        registry.addProvider(decoratorProvider)

        when:
        registry.get(String)

        then:
        ServiceCreationException e = thrown()
        e.message == "Could not create service of type String using ${decoratorProvider.class.simpleName}.${methodName}String() as this method returned null."

        where:
        decoratorProvider               | methodName
        new NullDecoratorWithCreate()   | 'create'
        new NullDecoratorWithDecorate() | 'decorate'
    }

    def usesFactoryMethodToCreateServiceInstance() {
        expect:
        registry.get(String.class) == "12"
        registry.get(Integer.class) == 12
    }

    def cachesInstancesCreatedUsingAFactoryMethod() {
        expect:
        registry.get(Integer).is(registry.get(Integer))
        registry.get(Number).is(registry.get(Number))
    }

    def usesOverriddenFactoryMethodToCreateServiceInstance() {
        def registry = new TestRegistry() {
            @Override
            protected String createString() {
                return "overridden"
            }
        };

        expect:
        registry.get(String) == "overridden"
    }

    def failsWhenMultipleFactoryMethodsCanCreateRequestedServiceType() {
        def registry = new DefaultServiceRegistry();
        registry.addProvider(new TestProvider())

        expect:
        registry.get(String)

        when:
        registry.get(Comparable)

        then:
        ServiceLookupException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""Multiple services of type Comparable available in DefaultServiceRegistry:
   - Service Integer at DefaultServiceRegistryTest\$TestProvider.createInt()
   - Service String at DefaultServiceRegistryTest\$TestProvider.createString()""")
    }

    def failsWhenArrayClassRequested() {
        when:
        registry.get(String[].class)

        then:
        ServiceLookupException e = thrown()
        e.message == "Locating services with array type is not supported."
    }

    def cannotInjectAnArrayType() {
        given:
        registry.addProvider(new UnsupportedInjectionProvider())

        when:
        registry.get(Number)

        then:
        ServiceCreationException e = thrown()
        e.message == "Cannot create service of type Number using method DefaultServiceRegistryTest\$UnsupportedInjectionProvider.create() as there is a problem with parameter #1 of type String[]."
        e.cause.message == 'Locating services with array type is not supported.'
    }

    def usesDecoratorMethodToDecorateParentServiceInstance() {
        def parent = Mock(ParentServices)
        def registry = decoratorCreator.call(registry(parent))  /* .call needed in spock 0.7 */

        when:
        def result = registry.get(Long)

        then:
        result == 120L

        and:
        1 * parent.get(Long) >> 110L

        where:
        decoratorCreator << [{ p -> new RegistryWithDecoratorMethodsWithCreate(p) }, { p -> new RegistryWithDecoratorMethodsWithDecorate(p) }]
    }

    def "decorator methods can take additional parameters"() {
        def parent = Mock(ParentServices)
        def registry = decoratorCreator.call(registry(parent))

        when:
        def result = registry.get(String)

        then:
        result == "Foo120"

        and:
        1 * parent.get(Long) >> 110L
        1 * parent.get(String) >> "Foo"

        where:
        decoratorCreator << [{ p -> new RegistryWithDecoratorMethodsWithCreate(p) }, { p -> new RegistryWithDecoratorMethodsWithDecorate(p) }]
    }

    def decoratorCreateMethodFailsWhenNoParentRegistry() {
        when:
        decoratorCreator.call() /* .call needed in spock 0.7 */

        then:
        ServiceLookupException e = thrown()
        e.message.matches(/Cannot use decorator method RegistryWithDecoratorMethodsWith(Create|Decorate)\..*\(\) when no parent registry is provided./)

        where:
        decoratorCreator << [{ new RegistryWithDecoratorMethodsWithCreate() }, { new RegistryWithDecoratorMethodsWithDecorate() }]
    }

    def canRegisterServicesUsingAction() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.register({ ServiceRegistration registration ->
            registration.add(Number, 12)
            registration.add(TestServiceImpl)
            registration.addProvider(new Object() {
                String createString() {
                    return "hi"
                }
            })
        } as Action)

        expect:
        registry.get(Number) == 12
        registry.get(TestServiceImpl)
        registry.get(String) == "hi"
    }

    def providerConfigureMethodCanRegisterServices() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new Object() {
            void configure(ServiceRegistration registration, Number value) {
                registration.addProvider(new Object() {
                    String createString() {
                        return value.toString()
                    }
                })
            }

            Integer createNumber() {
                return 123
            }
        })

        expect:
        registry.get(Number) == 123
        registry.get(String) == "123"
    }

    def failsWhenProviderConfigureMethodRequiresUnknownService() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new NoOpConfigureProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == 'Cannot configure services using NoOpConfigureProvider.configure() as required service of type String is not available.'
    }

    def failsWhenProviderConfigureMethodFails() {
        def registry = new DefaultServiceRegistry()

        when:
        registry.addProvider(new BrokenConfigureProvider())

        then:
        ServiceLookupException e = thrown()
        e.message == 'Could not configure services using BrokenConfigureProvider.configure().'
        e.cause == BrokenConfigureProvider.failure
    }

    def failsWhenCannotCreateServiceInstanceFromImplementationClass() {
        given:
        registry.register({ registration -> registration.add(ClassWithBrokenConstructor) } as Action)

        when:
        registry.get(ClassWithBrokenConstructor)

        then:
        ServiceCreationException e = thrown()
        e.message == 'Could not create service of type DefaultServiceRegistryTest$ClassWithBrokenConstructor.'
        e.cause == ClassWithBrokenConstructor.failure
    }

    def canGetAllServicesOfAGivenType() {
        registry.addProvider(new Object() {
            String createOtherString() {
                return "hi"
            }
        })

        expect:
        registry.getAll(String) == ["12", "hi"]
        registry.getAll(Number) == [12]
    }

    def removesDuplicateServicesWhenParentIsReachableViaMultiplePaths() {
        def root = new DefaultServiceRegistry()
        root.add(String, "root")
        def parent1 = new DefaultServiceRegistry(root)
        parent1.add(String, "p1")
        def parent2 = new DefaultServiceRegistry(root)
        parent2.add(String, "p2")
        def registry = new DefaultServiceRegistry(parent1, parent2)

        expect:
        registry.getAll(String) == ["p1", "root", "p2"]
    }

    def canGetAllServicesOfAGivenTypeUsingCollectionType() {
        registry.addProvider(new Object() {
            String createOtherString() {
                return "hi"
            }
        })

        expect:
        registry.get(new TypeToken<List<String>>() {}.type) == ["12", "hi"]
        registry.get(new TypeToken<List<Number>>() {}.type) == [12]
        registry.get(new TypeToken<List<? extends CharSequence>>() {}.type) == ["12", "hi"]
        registry.get(new TypeToken<List<? extends Number>>() {}.type) == [12]
    }

    def canGetAllServicesOfARawType() {
        def registry = new DefaultServiceRegistry()
        registry.addProvider(new Object() {
            String createString() {
                return "hi"
            }

            Factory<String> createFactory() {
                return {} as Factory
            }

            CharSequence createCharSequence() {
                return "foo"
            }
        })

        expect:
        registry.getAll(Factory).size() == 1
        registry.getAll(CharSequence).size() == 2
    }

    def allServicesReturnsEmptyCollectionWhenNoServicesOfGivenType() {
        expect:
        registry.getAll(Long).empty
    }

    def allServicesIncludesServicesFromParents() {
        def parent1 = Stub(ParentServices)
        def parent2 = Stub(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent1), registry(parent2))
        registry.addProvider(new Object() {
            Long createLong() {
                return 12;
            }
        });

        given:
        _ * parent1.getAll(Number) >> [123L]
        _ * parent2.getAll(Number) >> [456]

        expect:
        registry.getAll(Number) == [12, 123L, 456]
    }

    def allServicesDoesNotIncludeDecoratedServicesFromParents() {
        def parent1 = Stub(ParentServices)
        def parent2 = Stub(ParentServices)
        def registry = new DefaultServiceRegistry(registry(parent1), registry(parent2))
        registry.addProvider(new Object() {
            Long createLong(Long parent) {
                return parent + 1
            }
        });

        given:
        _ * parent1.get((Type) Long) >> 123L
        _ * parent1.getAll(Number) >> [123L]
        _ * parent2.getAll(Number) >> [456]

        expect:
        registry.getAll(Number) == [124L, 456]
    }

    def injectsAllServicesOfAGivenTypeIntoServiceImplementation() {
        def parent = new DefaultServiceRegistry()
        parent.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def registry = new DefaultServiceRegistry(parent)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithMultipleDependencies)
            registration.add(TestServiceImpl)
        }

        expect:
        registry.get(ServiceWithMultipleDependencies).services.size() == 2
        registry.get(ServiceWithMultipleDependencies).services == registry.getAll(TestServiceImpl)
        registry.get(ServiceWithMultipleDependencies).services == [registry.get(TestService), parent.get(TestService)]
    }

    def removesDuplicateinjectedServicesOfAGivenTypeWhenParentIsReachableFromMultiplePaths() {
        def root = new DefaultServiceRegistry()
        root.add(TestServiceImpl, new TestServiceImpl())
        def parent1 = new DefaultServiceRegistry(root)
        parent1.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def parent2 = new DefaultServiceRegistry(root)
        parent2.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def registry = new DefaultServiceRegistry(parent1, parent2)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithMultipleDependencies)
        }

        expect:
        registry.get(ServiceWithMultipleDependencies).services.size() == 3
        registry.get(ServiceWithMultipleDependencies).services == [parent1.get(TestService), root.get(TestService), parent2.get(TestService)]
    }

    def injectsEmptyListWhenNoServicesOfGivenType() {
        def parent = new DefaultServiceRegistry()
        def registry = new DefaultServiceRegistry(parent)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithMultipleDependencies)
        }

        expect:
        registry.get(ServiceWithMultipleDependencies).services.empty
    }

    def canUseWildcardsToInjectAllServicesWithType() {
        def parent = new DefaultServiceRegistry()
        parent.register { ServiceRegistration registration ->
            registration.add(TestServiceImpl)
        }
        def registry = new DefaultServiceRegistry(parent)
        registry.register { ServiceRegistration registration ->
            registration.add(ServiceWithWildCardDependencies)
            registration.add(TestServiceImpl)
        }

        expect:
        registry.get(ServiceWithWildCardDependencies).services.size() == 2
        registry.get(ServiceWithWildCardDependencies).services == registry.getAll(TestService)
    }

    def cannotUseLowerBoundWildcardToInjectAllServicesWithType() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new UnsupportedWildcardProvider())

        when:
        registry.get(Number)

        then:
        def e = thrown(ServiceCreationException)
        e.message == 'Cannot create service of type Number using method DefaultServiceRegistryTest\$UnsupportedWildcardProvider.create() as there is a problem with parameter #1 of type List<? super java.lang.String>.'
        e.cause.message == 'Locating services with type ? super java.lang.String is not supported.'
    }

    def canGetServiceAsFactoryWhenTheServiceImplementsFactoryInterface() {
        expect:
        registry.getFactory(BigDecimal) instanceof TestFactory
        registry.getFactory(Number) instanceof TestFactory
        registry.getFactory(BigDecimal).is(registry.getFactory(BigDecimal))
        registry.getFactory(Number).is(registry.getFactory(BigDecimal))
    }

    def canLocateFactoryWhenServiceInterfaceExtendsFactory() {
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(StringFactory, new StringFactory() {
            public String create() {
                return "value"
            }
        })

        expect:
        registry.getFactory(String.class).create() == "value"
    }

    def canGetAFactoryUsingParameterizedFactoryType() {
        def registry = new RegistryWithMultipleFactoryMethods()

        expect:
        def stringFactory = registry.get(stringFactoryType)
        stringFactory.create() == "hello"

        def numberFactory = registry.get(numberFactoryType)
        numberFactory.create() == 12
    }

    def canGetAFactoryUsingFactoryTypeWithBounds() throws NoSuchFieldException {
        expect:
        def superBigDecimalFactory = registry.get(superBigDecimalFactoryType)
        superBigDecimalFactory.create() == BigDecimal.valueOf(0)

        def extendsBigDecimalFactory = registry.get(extendsBigDecimalFactoryType)
        extendsBigDecimalFactory.create() == BigDecimal.valueOf(1)

        def extendsNumberFactory = registry.get(extendsNumberFactoryType)
        extendsNumberFactory.create() == BigDecimal.valueOf(2)
    }

    def usesAFactoryServiceToCreateInstances() {
        expect:
        registry.newInstance(BigDecimal) == BigDecimal.valueOf(0)
        registry.newInstance(BigDecimal) == BigDecimal.valueOf(1)
        registry.newInstance(BigDecimal) == BigDecimal.valueOf(2)
    }

    def throwsExceptionForUnknownFactory() {
        when:
        registry.getFactory(String)

        then:
        UnknownServiceException e = thrown()
        e.message == "No factory for objects of type String available in TestRegistry."
    }

    def delegatesToParentForUnknownFactory() {
        def factory = Mock(Factory)
        def parent = Mock(ParentServices)
        def registry = new TestRegistry(registry(parent))

        when:
        def result = registry.getFactory(Map)

        then:
        result == factory

        and:
        1 * parent.getFactory(Map) >> factory
    }

    def usesDecoratorMethodToDecorateParentFactoryInstance() {
        def factory = Mock(Factory)
        def parent = Mock(ParentServices)
        def registry = decoratorCreator.call(registry(parent))  /* .call needed in spock 0.7 */

        given:
        _ * parent.getFactory(Long) >> factory
        _ * factory.create() >>> [10L, 20L]

        expect:
        registry.newInstance(Long) == 12L
        registry.newInstance(Long) == 22L

        where:
        decoratorCreator << [{ p -> new RegistryWithDecoratorMethodsWithCreate(p) }, { p -> new RegistryWithDecoratorMethodsWithDecorate(p) }]
    }

    def failsWhenMultipleFactoriesAreAvailableForServiceType() {
        def registry = new RegistryWithAmbiguousFactoryMethods()

        when:
        registry.getFactory(Comparable)

        then:
        ServiceLookupException e = thrown()
        e.message == TextUtil.toPlatformLineSeparators("""Multiple factories for objects of type Comparable available in RegistryWithAmbiguousFactoryMethods:
   - Service Factory<Integer> at DefaultServiceRegistryTest\$RegistryWithAmbiguousFactoryMethods.createIntegerFactory()
   - Service Factory<String> at DefaultServiceRegistryTest\$RegistryWithAmbiguousFactoryMethods.createStringFactory()""")
    }

    def servicesCreatedByFactoryMethodsAreVisibleWhenUsingASubClass() {
        def registry = new TestRegistry() {
        }

        expect:
        registry.get(String) == "12"
        registry.get(Integer) == 12
    }

    def closeInvokesCloseMethodOnEachService() {
        def service = Mock(TestCloseService)

        given:
        registry.add(TestCloseService, service)

        when:
        registry.close()

        then:
        1 * service.close()
    }

    def closeInvokesStopMethodOnEachService() {
        def service = Mock(TestStopService)

        given:
        registry.add(TestStopService, service)

        when:
        registry.close()

        then:
        1 * service.stop()
    }

    def closeIgnoresServiceWithNoCloseOrStopMethod() {
        registry.add(String, "service")
        registry.getAll(String)

        when:
        registry.close()

        then:
        noExceptionThrown()
    }

    def closeInvokesCloseMethodOnEachServiceCreatedFromImplementationClass() {
        given:
        registry.register({ registration -> registration.add(CloseableService) } as Action)
        def service = registry.get(CloseableService)

        when:
        registry.close()

        then:
        service.closed
    }

    def closeInvokesCloseMethodOnEachServiceCreatedByProviderFactoryMethod() {
        def service = Mock(TestStopService)

        given:
        registry.addProvider(new Object() {
            TestStopService createServices() {
                return service
            }
        })
        registry.get(TestStopService)

        when:
        registry.close()

        then:
        1 * service.stop()
    }

    def closeClosesServicesInDependencyOrder() {
        def service1 = Mock(TestCloseService)
        def service2 = Mock(TestStopService)
        def service3 = Mock(CloseableService)
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new Object() {
            CloseableService createService3() {
                return service3
            }

            TestStopService createService2(CloseableService b) {
                return service2
            }

        })
        registry.addProvider(new Object() {
            TestCloseService createService1(TestStopService a, CloseableService b) {
                return service1
            }
        })
        registry.get(TestCloseService)

        when:
        registry.close()

        then:
        1 * service1.close()

        then:
        1 * service2.stop()

        then:
        1 * service3.close()
        0 * _._
    }

    def closeClosesServicesInDependencyOrderWhenServicesWithTypeInjected() {
        def service1 = Mock(TestStopService)
        def service2 = Mock(TestCloseService)
        def service3 = Mock(TestCloseService)
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new Object() {
            TestCloseService createService3() {
                return service3
            }

            TestCloseService createService2() {
                return service2
            }
        })
        registry.addProvider(new Object() {
            TestStopService createService1(List<TestCloseService> services) {
                return service1
            }
        })
        registry.get(TestStopService)

        when:
        registry.close()

        then:
        1 * service1.stop()

        then:
        1 * service2.close()
        1 * service3.close()
        0 * _._
    }

    def closeContinuesToCloseServicesAfterFailingToStopSomeService() {
        def service1 = Mock(TestCloseService)
        def service2 = Mock(TestStopService)
        def service3 = Mock(CloseableService)
        def failure = new RuntimeException()
        def registry = new DefaultServiceRegistry()

        given:
        registry.addProvider(new Object() {
            TestStopService createService2(CloseableService b) {
                return service2
            }

            TestCloseService createService1(TestStopService a) {
                return service1
            }

            CloseableService createService3() {
                return service3
            }
        })
        registry.get(TestCloseService)

        when:
        registry.close()

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * service1.close()
        1 * service2.stop() >> { throw failure }
        1 * service3.close()
        0 * _._
    }

    def doesNotStopServiceThatHasNotBeenCreated() {
        def service = Mock(TestStopService)

        given:
        registry.addProvider(new Object() {
            TestStopService createServices() {
                return service
            }
        })

        when:
        registry.close()

        then:
        0 * service.stop()
    }

    def canStopMultipleTimes() {
        def service = Mock(TestCloseService)

        given:
        registry.add(TestCloseService, service)

        when:
        registry.close()

        then:
        1 * service.close()

        when:
        registry.close()

        then:
        0 * service._
    }

    def cannotLookupServicesWhenClosed() {
        given:
        registry.get(String)
        registry.getAll(String)
        registry.close()

        when:
        registry.get(String)

        then:
        IllegalStateException e = thrown()
        e.message == "TestRegistry has been closed."

        when:
        registry.getAll(String)

        then:
        e = thrown()
        e.message == "TestRegistry has been closed."
    }

    def cannotLookupFactoriesWhenClosed() {
        given:
        registry.getFactory(BigDecimal)
        registry.close()

        when:
        registry.getFactory(BigDecimal)

        then:
        IllegalStateException e = thrown()
        e.message == "TestRegistry has been closed."
    }

    /*
     * Closing children would imply holding a reference to them. This would
     * create memory leaks.
     */

    def "does not close services from child registries"() {
        given:
        def parentService = Mock(TestCloseService)
        registry.addProvider(new Object() {
            Closeable createCloseableService() {
                parentService
            }
        })

        def child = new DefaultServiceRegistry(registry)
        def childService = Mock(TestStopService)
        child.addProvider(new Object() {
            Stoppable createCloseableService(Closeable dependency) {
                childService
            }
        })

        when:
        def service = child.get(Stoppable)
        registry.close()

        then:
        service == childService
        1 * parentService.close()
        0 * childService.close()
    }

    /*
     * We isolate services in child registries, so we don't leak memory. This test makes
     * sure that we don't overdo the isolation and still track dependencies between services
     * inside a single registry, even when a child requested that service.
     */

    def "closes services in dependency order even when child requested them first"() {
        def service1 = Mock(TestCloseService)
        def service2 = Mock(TestStopService)
        def service3 = Mock(CloseableService)
        def parent = new DefaultServiceRegistry()
        def child = new DefaultServiceRegistry(parent)

        given:
        parent.addProvider(new Object() {
            CloseableService createService3() {
                return service3
            }

            TestStopService createService2(CloseableService b) {
                return service2
            }
        })

        child.addProvider(new Object() {
            TestCloseService createService1(TestStopService a, CloseableService b) {
                return service1
            }
        })
        child.get(TestCloseService)

        when:
        parent.close()

        then:
        1 * service2.stop()

        then:
        1 * service3.close()
    }

    def "cannot add provider after getting a service via class"() {
        when:
        registry.get(Integer)
        registry.addProvider(new TestProvider())

        then:
        thrown IllegalStateException
    }

    def "cannot add provider after getting a service via type"() {
        when:
        registry.get(Integer as Type)
        registry.addProvider(new TestProvider())

        then:
        thrown IllegalStateException
    }

    def "cannot add provider after getting all services"() {
        when:
        registry.getAll(Integer)
        registry.addProvider(new TestProvider())

        then:
        thrown IllegalStateException
    }

    def "cannot add instance after getting a service via class"() {
        when:
        registry.get(Integer)
        registry.add(String, "foo")

        then:
        thrown IllegalStateException
    }

    def "cannot add instance after getting a service via type"() {
        when:
        registry.get(Integer as Type)
        registry.add(String, "foo")

        then:
        thrown IllegalStateException
    }

    def "cannot add instance after getting all services"() {
        when:
        registry.getAll(Integer)
        registry.add(String, "foo")

        then:
        thrown IllegalStateException
    }

    def "cannot lookup services while closing"() {
        given:
        registry.add(Closeable, { registry.get(String) } as Closeable)

        when:
        registry.close()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("closed")
    }

    def MockServiceRegistry registry(ParentServices parentServices) {
        return new MockServiceRegistry(parentServices)
    }

    private Factory<Number> numberFactory
    private Factory<String> stringFactory
    private Factory<? super BigDecimal> superBigDecimalFactory
    private Factory<? extends BigDecimal> extendsBigDecimalFactory
    private Factory<? extends Number> extendsNumberFactory

    private Type getNumberFactoryType() {
        return getClass().getDeclaredField("numberFactory").getGenericType();
    }

    private Type getStringFactoryType() {
        return getClass().getDeclaredField("stringFactory").getGenericType();
    }

    private Type getSuperBigDecimalFactoryType() {
        return getClass().getDeclaredField("superBigDecimalFactory").getGenericType()
    }

    private Type getExtendsBigDecimalFactoryType() {
        return getClass().getDeclaredField("extendsBigDecimalFactory").getGenericType()
    }

    private Type getExtendsNumberFactoryType() {
        return getClass().getDeclaredField("extendsNumberFactory").getGenericType()
    }

    /**
     * A simplified view of {@link ServiceRegistry}
     */
    interface ParentServices {
        Object get(Class<?> type)

        List<Object> getAll(Class<?> type)

        Factory<?> getFactory(Class<?> type)
    }

    private static class MockServiceWrapper implements Service {
        private final Object instance

        MockServiceWrapper(Object instance) {
            this.instance = instance
        }

        @Override
        String getDisplayName() {
            throw new UnsupportedOperationException()
        }

        @Override
        Object get() {
            return instance
        }

        @Override
        void requiredBy(ServiceProvider serviceProvider) {
        }
    }

    private static class MockServiceProvider implements ServiceProvider {
        private final ParentServices parentServices
        private final Map<Object, Service> services = [:]

        MockServiceProvider(ParentServices parentServices) {
            this.parentServices = parentServices
        }

        @Override
        Service getService(Type serviceType) {
            def object = parentServices.get((Class) serviceType)
            if (object == null) {
                return null
            }
            return serviceFor(object)
        }

        @Override
        Service getFactory(Class<?> type) {
            def factory = parentServices.getFactory(type)
            if (factory == null) {
                return factory
            }
            return serviceFor(factory)
        }

        @Override
        Visitor getAll(Class<?> serviceType, Visitor visitor) {
            parentServices.getAll(serviceType).forEach {
                visitor.visit(serviceFor(it))
            }
            return visitor
        }

        @Override
        void stop() {
            throw new UnsupportedOperationException()
        }

        Service serviceFor(Object object) {
            def service = services.get(object)
            if (service == null) {
                service = new MockServiceWrapper(object)
                services.put(object, service)
            }
            return service
        }
    }

    @NonNullApi
    private static class MockServiceRegistry implements ContainsServices, ServiceRegistry {
        private final ParentServices parentServices

        MockServiceRegistry(ParentServices parentServices) {
            this.parentServices = parentServices
        }

        @Override
        ServiceProvider asProvider() {
            return new MockServiceProvider(parentServices)
        }

        @Override
        def <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException {
            throw new UnsupportedOperationException()
        }

        @Override
        Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            throw new UnsupportedOperationException()
        }

        @Override
        Object find(Type serviceType) throws ServiceLookupException {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> Factory<T> getFactory(Class<T> type) throws UnknownServiceException, ServiceLookupException {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> T newInstance(Class<T> type) throws UnknownServiceException, ServiceLookupException {
            throw new UnsupportedOperationException()
        }

        @Override
        Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            throw new UnsupportedOperationException()
        }
    }

    private static class TestFactory implements Factory<BigDecimal> {
        int value;

        public BigDecimal create() {
            return BigDecimal.valueOf(value++)
        }
    }

    private interface TestService {
    }

    private static class TestServiceImpl implements TestService {
    }

    private static class ServiceWithDependency {
        final TestService service

        ServiceWithDependency(TestService service) {
            this.service = service
        }
    }

    private static class ServiceWithMultipleDependencies {
        final List<TestService> services

        ServiceWithMultipleDependencies(List<TestService> services) {
            this.services = services
        }
    }

    private static class ServiceWithWildCardDependencies {
        final List<?> services

        ServiceWithWildCardDependencies(List<? extends TestService> services) {
            this.services = services
        }
    }

    private interface StringFactory extends Factory<String> {
    }

    private static class TestRegistry extends DefaultServiceRegistry {
        public TestRegistry() {
        }

        public TestRegistry(ServiceRegistry parent) {
            super(parent)
        }

        protected String createString() {
            return get(Integer).toString()
        }

        protected Integer createInt() {
            return 12
        }

        protected Factory<BigDecimal> createTestFactory() {
            return new TestFactory()
        }
    }

    private static class TestProvider {
        String createString(Integer integer) {
            return integer.toString()
        }

        Integer createInt() {
            return 12
        }

        Factory<BigDecimal> createTestFactory() {
            return new TestFactory()
        }

        Callable<BigDecimal> createCallable() {
            return { 12 }
        }
    }

    private static class StringProvider {
        String createString(Runnable r) {
            return "hi"
        }

        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class ProviderWithCycle {
        String createString(Integer value) {
            return value.toString()
        }

        Integer createInteger(String value) {
            return value.length()
        }
    }

    private static class NullProvider {
        String createString() {
            return null
        }
    }

    private static class UnsupportedInjectionProvider {
        Number create(String[] values) {
            return values.length
        }
    }

    private static class UnsupportedWildcardProvider {
        Number create(List<? super String> values) {
            return values.length
        }
    }

    private static class NoOpConfigureProvider {
        void configure(ServiceRegistration registration, String value) {
        }
    }

    private static class BrokenConfigureProvider {
        static def failure = new RuntimeException()

        void configure(ServiceRegistration registration) {
            throw failure
        }
    }

    private static class BrokenProvider {
        static def failure = new RuntimeException()

        String createString() {
            throw failure.fillInStackTrace()
        }

        Integer createInteger(String value) {
            return value.length()
        }
    }


    private static class ConflictingDecoratorMethods {
        Long createLong(Long value) {
            return value + 2
        }

        Long decorateLong(Long value) {
            return value + 2
        }
    }

    private static class TestDecoratingProviderWithCreate {
        Long createLong(Long value) {
            return value + 2
        }
    }

    private static class TestDecoratingProviderWithDecorate {
        Long decorateLong(Long value) {
            return value + 2
        }
    }

    private static class BrokenDecoratingProviderWithCreate {
        static def failure = new RuntimeException()

        Long createLong(Long value) {
            throw failure
        }
    }

    private static class BrokenDecoratingProviderWithDecorate {
        static def failure = new RuntimeException()

        Long decorateLong(Long value) {
            throw failure
        }
    }

    private static class NullDecoratorWithCreate {
        String createString(String value) {
            return null
        }
    }

    private static class NullDecoratorWithDecorate {
        String decorateString(String value) {
            return null
        }
    }

    private static class RegistryWithAmbiguousFactoryMethods extends DefaultServiceRegistry {
        Integer createInteger() {
            return 123
        }

        String createString() {
            return "hello"
        }

        Factory<Integer> createIntegerFactory() {
            return new Factory<Integer>() {
                public Integer create() {
                    return createInteger()
                }
            };
        }

        Factory<String> createStringFactory() {
            return new Factory<String>() {
                public String create() {
                    return createString()
                }
            };
        }
    }

    private static class RegistryWithDecoratorMethodsWithCreate extends DefaultServiceRegistry {
        public RegistryWithDecoratorMethodsWithCreate() {
        }

        public RegistryWithDecoratorMethodsWithCreate(ServiceRegistry parent) {
            super(parent)
        }

        protected Long createLong(Long value) {
            return value + 10
        }

        protected Factory<Long> createLongFactory(final Factory<Long> factory) {
            return new Factory<Long>() {
                public Long create() {
                    return factory.create() + 2
                }
            };
        }

        protected String createString(String parentValue, Long myValue) {
            return parentValue + myValue
        }
    }

    private static class RegistryWithDecoratorMethodsWithDecorate extends DefaultServiceRegistry {
        public RegistryWithDecoratorMethodsWithDecorate() {
        }

        public RegistryWithDecoratorMethodsWithDecorate(ServiceRegistry parent) {
            super(parent)
        }

        protected Long decorateLong(Long value) {
            return value + 10
        }

        protected Factory<Long> decorateLongFactory(final Factory<Long> factory) {
            return new Factory<Long>() {
                public Long create() {
                    return factory.create() + 2
                }
            };
        }

        protected String decorateString(String parentValue, Long myValue) {
            return parentValue + myValue
        }
    }

    private static class RegistryWithMultipleFactoryMethods extends DefaultServiceRegistry {
        Factory<Number> createObjectFactory() {
            return new Factory<Number>() {
                public Number create() {
                    return 12
                }
            };
        }

        Factory<String> createStringFactory() {
            return new Factory<String>() {
                public String create() {
                    return "hello"
                }
            };
        }
    }

    interface TestCloseService extends Closeable {
        void close()
    }

    interface TestStopService extends Stoppable {
        void stop()
    }

    static class ClassWithBrokenConstructor {
        static def failure = new RuntimeException("broken")

        ClassWithBrokenConstructor() {
            throw failure
        }
    }

    static class CloseableService implements Closeable {
        boolean closed

        void close() {
            closed = true
        }
    }

    static class RequiresService {
        RequiresService(Number value) {
        }
    }
}
