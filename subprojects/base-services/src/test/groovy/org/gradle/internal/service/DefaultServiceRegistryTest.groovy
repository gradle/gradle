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

import org.gradle.internal.Factory
import spock.lang.Specification

import java.lang.reflect.Type

class DefaultServiceRegistryTest extends Specification {
    def TestRegistry registry = new TestRegistry()

    def throwsExceptionForUnknownService() {
        when:
        registry.get(StringBuilder.class)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No service of type StringBuilder available in TestRegistry."
    }

    def delegatesToParentForUnknownService() {
        def value = BigDecimal.TEN
        def parent = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        when:
        def result = registry.get(BigDecimal)

        then:
        result == value

        and:
        1 * parent.get(BigDecimal) >> value
    }

    def throwsExceptionForUnknownParentService() {
        def parent = Mock(ServiceRegistry);
        def registry = new TestRegistry(parent)

        given:
        _ * parent.get(StringBuilder) >> { throw new UnknownServiceException(StringBuilder.class, "fail") }

        when:
        registry.get(StringBuilder)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No service of type StringBuilder available in TestRegistry."
    }

    def returnsAddedServiceInstance() {
        def value = BigDecimal.TEN
        def registry = new DefaultServiceRegistry()

        given:
        registry.add(BigDecimal, value)

        expect:
        registry.get(BigDecimal) == value
        registry.get(Number) == value
        registry.get(Object) == value
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

    public void failsWhenMultipleServiceFactoriesCanCreateRequestedServiceType() {
        def registry = new RegistryWithAmbiguousFactoryMethods();

        expect:
        registry.get(String) == "hello"

        when:
        registry.get(Object)

        then:
        ServiceLookupException e = thrown()
        e.message == "Multiple services of type Object available in RegistryWithAmbiguousFactoryMethods."
    }

    def failsWhenArrayClassRequested() {
        when:
        registry.get(String[].class)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot locate service of array type String[]."
    }

    def usesDecoratorMethodToDecorateParentServiceInstance() {
        def parent = Mock(ServiceRegistry)
        def registry = new RegistryWithDecoratorMethods(parent)

        when:
        def result = registry.get(Long)

        then:
        result == 120L

        and:
        1 * parent.get(Long) >> 110L
    }

    def decoratorMethodFailsWhenNoParentRegistry() {
        when:
        new RegistryWithDecoratorMethods()

        then:
        ServiceLookupException e = thrown()
        e.message == "Cannot use decorator methods when no parent registry is provided."
    }

    def canGetAllServicesOfAGivenType() {
        expect:
        registry.getAll(String) == ["12"]
        registry.getAll(Number) == [12]
    }

    def returnsNullWhenNoServicesOfGivenType() {
        expect:
        registry.getAll(Long).empty
    }

    def includesServicesFromParent() {
        def registry = new DefaultServiceRegistry(registry) {
            Long createLong() {
                return 123L;
            }
        };

        expect:
        registry.getAll(Number) == [123L, 12]
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

    def cannotGetAFactoryUsingRawFactoryType() {
        when:
        registry.get(Factory)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot locate service of raw type Factory. Use getFactory() or get(Type) instead."
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
        IllegalArgumentException e = thrown()
        e.message == "No factory for objects of type String available in TestRegistry."
    }

    def delegatesToParentForUnknownFactory() {
        def factory = Mock(Factory)
        def parent = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        when:
        def result = registry.getFactory(Map)

        then:
        result == factory

        and:
        1 * parent.getFactory(Map) >> factory
    }

    def usesDecoratorMethodToDecorateParentFactoryInstance() {
        def factory = Mock(Factory)
        def parent = Mock(ServiceRegistry)
        def registry = new RegistryWithDecoratorMethods(parent)

        given:
        _ * parent.getFactory(Long) >> factory
        _ * factory.create() >>> [10L, 20L]

        expect:
        registry.newInstance(Long) == 12L
        registry.newInstance(Long) == 22L
    }

    def failsWhenMultipleFactoriesAreAvailableForServiceType() {
        def registry = new RegistryWithAmbiguousFactoryMethods()

        when:
        registry.getFactory(Object)

        then:
        ServiceLookupException e = thrown()
        e.message == "Multiple factories for objects of type Object available in RegistryWithAmbiguousFactoryMethods."
    }

    def returnsServiceInstancesManagedByNestedServiceRegistry() {
        def nested = Mock(ServiceRegistry)
        def runnable = Mock(Runnable)

        given:
        registry.add(nested)

        when:
        def result = registry.get(Runnable)

        then:
        result == runnable

        and:
        1 * nested.get(Runnable) >> runnable
    }

    def throwsExceptionForUnknownServiceInNestedRegistry() {
        def nested = Mock(ServiceRegistry)

        given:
        registry.add(nested)
        _ * nested.get(Runnable) >> { throw new UnknownServiceException(Runnable, "fail") }

        when:
        registry.get(Runnable)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No service of type Runnable available in TestRegistry."
    }

    def returnsServiceFactoriesManagedByNestedServiceRegistry() {
        def nested = Mock(ServiceRegistry)
        def factory = Mock(Factory)

        given:
        registry.add(nested)

        when:
        def result = registry.getFactory(Runnable)

        then:
        result == factory

        and:
        1 * nested.getFactory(Runnable) >> factory
    }

    def throwsExceptionForUnknownFactoryInNestedRegistry() {
        def nested = Mock(ServiceRegistry)

        given:
        registry.add(nested)
        _ * nested.getFactory(Runnable) >> { throw new UnknownServiceException(Runnable, "fail") }

        when:
        registry.getFactory(Runnable)

        then:
        IllegalArgumentException e = thrown()
        e.message == "No factory for objects of type Runnable available in TestRegistry."
    }

    def servicesCreatedByFactoryMethodsAreVisibleWhenUsingASubClass() {
        def registry = new TestRegistry() {
        }

        expect:
        registry.get(String) == "12"
        registry.get(Integer) == 12
    }

    def prefersServicesCreatedByFactoryMethodsOverNestedServices() {
        def parent = Mock(ServiceRegistry)
        def nested = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        given:
        registry.add(nested)

        expect:
        registry.get(String) == "12"
    }

    def prefersRegisteredServicesOverNestedServices() {
        def parent = Mock(ServiceRegistry)
        def nested = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)

        given:
        registry.add(nested)
        registry.add(BigDecimal, BigDecimal.ONE)

        expect:
        registry.get(BigDecimal.class) == BigDecimal.ONE
    }

    def prefersNestedServicesOverParentServices() {
        def parent = Mock(ServiceRegistry)
        def nested = Mock(ServiceRegistry)
        def registry = new TestRegistry(parent)
        def runnable = Mock(Runnable)

        given:
        registry.add(nested);

        when:
        def result = registry.get(Runnable)

        then:
        result == runnable

        and:
        1 * nested.get(Runnable) >> runnable
        0 * _._
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

        when:
        registry.close()

        then:
        noExceptionThrown()
    }

    public void closeInvokesCloseMethodOnEachNestedServiceRegistry() {
        def nested = Mock(ClosableServiceRegistry)

        given:
        registry.add(nested)

        when:
        registry.close()

        then:
        1 * nested.close()
    }

    def discardsServicesOnClose() {
        given:
        registry.get(String)
        registry.close()

        when:
        registry.get(String)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot locate service of type String, as TestRegistry has been closed."
    }

    def discardsFactoriesOnClose() {
        given:
        registry.getFactory(BigDecimal)
        registry.close()

        when:
        registry.getFactory(BigDecimal)

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot locate factory for objects of type BigDecimal, as TestRegistry has been closed."
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

    private static class TestFactory implements Factory<BigDecimal> {
        int value;
        public BigDecimal create() {
            return BigDecimal.valueOf(value++)
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

    private static class RegistryWithAmbiguousFactoryMethods extends DefaultServiceRegistry {
        Object createObject() {
            return "hello"
        }

        String createString() {
            return "hello"
        }

        Factory<Object> createObjectFactory() {
            return new Factory<Object>() {
                public Object create() {
                    return createObject()
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

    private static class RegistryWithDecoratorMethods extends DefaultServiceRegistry {
        public RegistryWithDecoratorMethods() {
        }

        public RegistryWithDecoratorMethods(ServiceRegistry parent) {
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

    public interface TestCloseService {
        void close()
    }

    public interface TestStopService {
        void stop()
    }

    public interface ClosableServiceRegistry extends ServiceRegistry {
        void close()
    }
}
