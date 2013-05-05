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
package org.gradle.internal.service;

import org.gradle.internal.Factory;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TestRegistry registry = new TestRegistry();

    @Test
    public void throwsExceptionForUnknownService() {
        try {
            registry.get(StringBuilder.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type StringBuilder available in TestRegistry."));
        }
    }

    @Test
    public void delegatesToParentForUnknownService() {
        final BigDecimal value = BigDecimal.TEN;
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        TestRegistry registry = new TestRegistry(parent);

        context.checking(new Expectations(){{
            one(parent).get(BigDecimal.class);
            will(returnValue(value));
        }});

        assertThat(registry.get(BigDecimal.class), sameInstance(value));
    }

    @Test
    public void throwsExceptionForUnknownParentService() {
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        TestRegistry registry = new TestRegistry(parent);

        context.checking(new Expectations(){{
            one(parent).get(StringBuilder.class);
            will(throwException(new UnknownServiceException(StringBuilder.class, "fail")));
        }});

        try {
            registry.get(StringBuilder.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type StringBuilder available in TestRegistry."));
        }
    }

    @Test
    public void returnsAddedServiceInstance() {
        BigDecimal value = BigDecimal.TEN;
        DefaultServiceRegistry registry = new DefaultServiceRegistry();
        registry.add(BigDecimal.class, value);
        assertThat(registry.get(BigDecimal.class), sameInstance(value));
        assertThat(registry.get(Number.class), sameInstance((Object) value));
    }

    @Test
    public void cachesRegisteredServiceInstance() {
        assertThat(registry.get(Integer.class), sameInstance(registry.get(Integer.class)));
        assertThat(registry.get(Integer.class), sameInstance((Object) registry.get(Integer.class)));
    }

    @Test
    public void usesFactoryMethodToCreateServiceInstance() {
        assertThat(registry.get(String.class), equalTo("12"));
        assertThat(registry.get(Integer.class), equalTo(12));
    }

    @Test
    public void usesOverriddenFactoryMethodToCreateServiceInstance() {
        TestRegistry registry = new TestRegistry(){
            @Override
            protected String createString() {
                return "overridden";
            }
        };
        assertThat(registry.get(String.class), equalTo("overridden"));
    }

    @Test
    public void failsWhenMultipleServiceFactoriesCanCreateRequestedServiceType() {
        ServiceRegistry registry = new RegistryWithAmbiguousFactoryMethods();
        assertThat(registry.get(String.class), equalTo("hello"));

        try {
            registry.get(Object.class);
            fail();
        } catch (ServiceLookupException e) {
            assertThat(e.getMessage(), equalTo("Multiple services of type Object available in RegistryWithAmbiguousFactoryMethods."));
        }
    }

    @Test
    public void failsWhenArrayClassRequested() {
        try {
            registry.get(String[].class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("Cannot locate service of array type String[]."));
        }
    }

    @Test
    public void usesDecoratorMethodToDecorateParentServiceInstance() {
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        ServiceRegistry registry = new RegistryWithDecoratorMethods(parent);

        context.checking(new Expectations() {{
            one(parent).get(Long.class);
            will(returnValue(110L));
        }});

        assertThat(registry.get(Long.class), equalTo(120L));
    }

    @Test
    public void decoratorMethodFailsWhenNoParentRegistry() {
        try {
            new RegistryWithDecoratorMethods();
            fail();
        } catch (ServiceLookupException e) {
            assertThat(e.getMessage(), equalTo("Cannot use decorator methods when no parent registry is provided."));
        }
    }

    @Test
    public void canGetServiceAsFactoryWhenTheServiceImplementsFactoryInterface() {
        assertThat(registry.getFactory(BigDecimal.class), instanceOf(TestFactory.class));
        assertThat(registry.getFactory(BigDecimal.class), sameInstance((Object) registry.getFactory(BigDecimal.class)));
        assertThat(registry.getFactory(Number.class), sameInstance((Object) registry.getFactory(BigDecimal.class)));
    }

    @Test
    public void canLocateFactoryWhenServiceInterfaceExtendsFactory() {
        registry.add(StringFactory.class, new StringFactory() {
            public String create() {
                return "value";
            }
        });
        assertThat(registry.getFactory(String.class).create(), equalTo("value"));
    }

    @Test
    public void canGetAFactoryUsingParameterisedFactoryType() throws NoSuchFieldException {
        ServiceRegistry registry = new RegistryWithMultipleFactoryMethods();

        Factory<String> stringFactory = (Factory<String>) registry.get(getStringFactoryType());
        assertEquals(stringFactory.create(), "hello");

        Factory<Number> numberFactory = (Factory<Number>) registry.get(getNumberFactoryType());
        assertEquals(numberFactory.create(), 12);
    }

    @Test
    public void canGetAFactoryUsingFactoryTypeWithBounds() throws NoSuchFieldException {
        Factory<? super BigDecimal> superBigDecimalFactory = (Factory<? super BigDecimal>) registry.get(getSuperBigDecimalFactoryType());
        assertEquals(superBigDecimalFactory.create(), BigDecimal.valueOf(0));

        Factory<? extends BigDecimal> extendsBigDecimalFactory = (Factory<? extends BigDecimal>) registry.get(getExtendsBigDecimalFactoryType());
        assertEquals(extendsBigDecimalFactory.create(), BigDecimal.valueOf(1));

        Factory<? extends Number> extendsNumberFactory = (Factory<? extends Number>) registry.get(getExtendsNumberFactoryType());
        assertEquals(extendsNumberFactory.create(), BigDecimal.valueOf(2));
    }

    @Test
    public void cannotGetAFactoryUsingRawFactoryType() {
        try {
            registry.get(Factory.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("Cannot locate service of raw type Factory. Use getFactory() or get(Type) instead."));
        }
    }

    @Test
    public void usesAFactoryServiceToCreateInstances() {
        assertThat(registry.newInstance(BigDecimal.class), equalTo(BigDecimal.valueOf(0)));
        assertThat(registry.newInstance(BigDecimal.class), equalTo(BigDecimal.valueOf(1)));
        assertThat(registry.newInstance(BigDecimal.class), equalTo(BigDecimal.valueOf(2)));
    }

    @Test
    public void delegatesToParentForUnknownFactory() {
        @SuppressWarnings("unchecked") final Factory<Map> factory = context.mock(Factory.class);
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        TestRegistry registry = new TestRegistry(parent);

        context.checking(new Expectations() {{
            one(parent).getFactory(Map.class);
            will(returnValue(factory));
        }});

        assertThat(registry.getFactory(Map.class), sameInstance((Object) factory));
    }

    @Test
    public void usesDecoratorMethodToDecorateParentFactoryInstance() {
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        @SuppressWarnings("unchecked") final Factory<Long> factory = context.mock(Factory.class);
        ServiceRegistry registry = new RegistryWithDecoratorMethods(parent);

        context.checking(new Expectations() {{
            one(parent).getFactory(Long.class);
            will(returnValue(factory));
            allowing(factory).create();
            will(onConsecutiveCalls(returnValue(10L), returnValue(20L)));
        }});

        assertThat(registry.newInstance(Long.class), equalTo(12L));
        assertThat(registry.newInstance(Long.class), equalTo(22L));
    }
    
    @Test
    public void throwsExceptionForUnknownFactory() {
        try {
            registry.getFactory(String.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No factory for objects of type String available in TestRegistry."));
        }
    }

    @Test
    public void failsWhenMultipleFactoriesAreAvailableForServiceType() {
        ServiceRegistry registry = new RegistryWithAmbiguousFactoryMethods();

        try {
            registry.getFactory(Object.class);
            fail();
        } catch (ServiceLookupException e) {
            assertThat(e.getMessage(), equalTo("Multiple factories for objects of type Object available in RegistryWithAmbiguousFactoryMethods."));
        }
    }

    @Test
    public void returnsServiceInstancesManagedByNestedServiceRegistry() {
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        final Runnable runnable = context.mock(Runnable.class);
        registry.add(nested);

        context.checking(new Expectations() {{
            one(nested).get(Runnable.class);
            will(returnValue(runnable));
        }});

        assertThat(registry.get(Runnable.class), sameInstance(runnable));
    }

    @Test
    public void throwsExceptionForUnknownServiceInNestedRegistry() {
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        registry.add(nested);

        context.checking(new Expectations(){{
            one(nested).get(Runnable.class);
            will(throwException(new UnknownServiceException(Runnable.class, "fail")));
        }});

        try {
            registry.get(Runnable.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type Runnable available in TestRegistry."));
        }
    }

    @Test
    public void returnsServiceFactoriesManagedByNestedServiceRegistry() {
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        @SuppressWarnings("unchecked") final Factory<Runnable> factory = context.mock(Factory.class);
        registry.add(nested);

        context.checking(new Expectations() {{
            one(nested).getFactory(Runnable.class);
            will(returnValue(factory));
        }});

        assertThat(registry.getFactory(Runnable.class), sameInstance(factory));
    }

    @Test
    public void throwsExceptionForUnknownFactoryInNestedRegistry() {
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        registry.add(nested);

        context.checking(new Expectations(){{
            one(nested).getFactory(Runnable.class);
            will(throwException(new UnknownServiceException(Runnable.class, "fail")));
        }});

        try {
            registry.getFactory(Runnable.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No factory for objects of type Runnable available in TestRegistry."));
        }
    }

    @Test
    public void servicesCreatedByFactoryMethodsAreVisibleWhenUsingASubClass() {
        ServiceRegistry registry = new SubType();
        assertThat(registry.get(String.class), equalTo("12"));
        assertThat(registry.get(Integer.class), equalTo(12));
    }
    
    @Test
    public void closeInvokesCloseMethodOnEachService() {
        final TestCloseService service = context.mock(TestCloseService.class);
        registry.add(TestCloseService.class, service);

        context.checking(new Expectations() {{
            one(service).close();
        }});

        registry.close();
    }

    @Test
    public void prefersServicesCreatedByFactoryMethodsOverNestedServices() {
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        final TestRegistry registry = new TestRegistry(parent);
        registry.add(nested);

        assertThat(registry.get(String.class), equalTo("12"));
    }

    @Test
    public void prefersRegisteredServicesOverNestedServices() {
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        final TestRegistry registry = new TestRegistry(parent);
        registry.add(nested);
        registry.add(BigDecimal.class, BigDecimal.ONE);

        assertThat(registry.get(BigDecimal.class), equalTo(BigDecimal.ONE));
    }

    @Test
    public void prefersNestedServicesOverParentServices() {
        final ServiceRegistry parent = context.mock(ServiceRegistry.class);
        final ServiceRegistry nested = context.mock(ServiceRegistry.class);
        final TestRegistry registry = new TestRegistry(parent);
        final Runnable runnable = context.mock(Runnable.class);
        registry.add(nested);

        context.checking(new Expectations() {{
            one(nested).get(Runnable.class);
            will(returnValue(runnable));
        }});

        assertThat(registry.get(Runnable.class), sameInstance(runnable));
    }

    @Test
    public void closeInvokesStopMethodOnEachService() {
        final TestStopService service = context.mock(TestStopService.class);
        registry.add(TestStopService.class, service);

        context.checking(new Expectations() {{
            one(service).stop();
        }});

        registry.close();
    }

    @Test
    public void closeIgnoresServiceWithNoCloseOrStopMethod() {
        registry.add(String.class, "service");

        registry.close();
    }

    @Test
    public void closeInvokesCloseMethodOnEachNestedServiceRegistry() {
        final ClosableServiceRegistry nested = context.mock(ClosableServiceRegistry.class);
        registry.add(nested);

        context.checking(new Expectations() {{
            one(nested).close();
        }});

        registry.close();
    }

    @Test
    public void discardsServicesOnClose() {
        registry.get(String.class);
        registry.close();
        try {
            registry.get(String.class);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Cannot locate service of type String, as TestRegistry has been closed."));
        }
    }

    @Test
    public void discardsFactoriesOnClose() {
        registry.getFactory(BigDecimal.class);
        registry.close();
        try {
            registry.getFactory(BigDecimal.class);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Cannot locate factory for objects of type BigDecimal, as TestRegistry has been closed."));
        }
    }

    private Factory<Number> numberFactory;
    private Factory<String> stringFactory;
    private Factory<? super BigDecimal> superBigDecimalFactory;
    private Factory<? extends BigDecimal> extendsBigDecimalFactory;
    private Factory<? extends Number> extendsNumberFactory;

    private Type getNumberFactoryType() throws NoSuchFieldException {
        return getClass().getDeclaredField("numberFactory").getGenericType();
    }

    private Type getStringFactoryType() throws NoSuchFieldException {
        return getClass().getDeclaredField("stringFactory").getGenericType();
    }

    private Type getSuperBigDecimalFactoryType() throws NoSuchFieldException {
        return getClass().getDeclaredField("superBigDecimalFactory").getGenericType();
    }

    private Type getExtendsBigDecimalFactoryType() throws NoSuchFieldException {
        return getClass().getDeclaredField("extendsBigDecimalFactory").getGenericType();
    }

    private Type getExtendsNumberFactoryType() throws NoSuchFieldException {
        return getClass().getDeclaredField("extendsNumberFactory").getGenericType();
    }

    private static class TestRegistry extends DefaultServiceRegistry {
        public TestRegistry() {
        }

        public TestRegistry(ServiceRegistry parent) {
            super(parent);
        }

        protected String createString() {
            return get(Integer.class).toString();
        }

        protected Integer createInt() {
            return 12;
        }

        protected Factory<BigDecimal> createTestFactory() {
            return new TestFactory();
        }
    }

    private static class RegistryWithDecoratorMethods extends DefaultServiceRegistry {
        public RegistryWithDecoratorMethods() {
        }

        public RegistryWithDecoratorMethods(ServiceRegistry parent) {
            super(parent);
        }

        protected Long createLong(Long value) {
            return value + 10;
        }

        protected Factory<Long> createLongFactory(final Factory<Long> factory) {
            return new Factory<Long>() {
                public Long create() {
                    return factory.create() + 2;
                }
            };
        }
    }

    private static class SubType extends TestRegistry {
    }

    private static class RegistryWithAmbiguousFactoryMethods extends DefaultServiceRegistry {
        Object createObject() {
            return "hello";
        }
        
        String createString() {
            return "hello";
        }
        
        Factory<Object> createObjectFactory() {
            return new Factory<Object>() {
                public Object create() {
                    return createObject();
                }
            };
        }
        
        Factory<String> createStringFactory() {
            return new Factory<String>() {
                public String create() {
                    return createString();
                }
            };
        }
    }

    private static class RegistryWithMultipleFactoryMethods extends DefaultServiceRegistry {
        Factory<Number> createObjectFactory() {
            return new Factory<Number>() {
                public Number create() {
                    return 12;
                }
            };
        }

        Factory<String> createStringFactory() {
            return new Factory<String>() {
                public String create() {
                    return "hello";
                }
            };
        }
    }

    private static class TestFactory implements Factory<BigDecimal> {
        int value;
        public BigDecimal create() {
            return BigDecimal.valueOf(value++);
        }
    }

    private interface StringFactory extends Factory<String> {
    }

    public interface TestCloseService {
        void close();
    }

    public interface TestStopService {
        void stop();
    }

    public interface ClosableServiceRegistry extends ServiceRegistry {
        void close();
    }
}
