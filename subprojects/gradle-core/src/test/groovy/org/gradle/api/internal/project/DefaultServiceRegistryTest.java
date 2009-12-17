/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final TestRegistry registry = new TestRegistry();

    @Test
    public void throwsExceptionForUnknownService() {
        try {
            registry.get(Map.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type Map available in TestRegistry."));
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
            one(parent).get(Map.class);
            will(throwException(new DefaultServiceRegistry.UnknownServiceException(Map.class, "fail")));
        }});

        try {
            registry.get(Map.class);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("No service of type Map available in TestRegistry."));
        }
    }

    @Test
    public void returnsAddedServiceInstance() {
        BigDecimal value = BigDecimal.TEN;
        registry.add(BigDecimal.class, value);
        assertThat(registry.get(BigDecimal.class), sameInstance(value));
        assertThat(registry.get(Number.class), sameInstance((Object) value));
    }

    @Test
    public void createsAnCachesRegisteredServiceInstance() {
        final BigDecimal value = BigDecimal.TEN;
        registry.add(new DefaultServiceRegistry.Service(BigDecimal.class) {
            @Override
            protected Object create() {
                return value;
            }
        });
        assertThat(registry.get(BigDecimal.class), sameInstance(value));
        assertThat(registry.get(Number.class), sameInstance((Object) value));
    }

    @Test
    public void usesFactoryMethodToCreateServiceInstance() {
        assertThat(registry.get(String.class), equalTo("12"));
        assertThat(registry.get(Integer.class), equalTo(12));
    }

    @Test
    public void closeInvokesCloseMethodOnEachService() {
        final TestService service = context.mock(TestService.class);
        registry.add(TestService.class, service);

        context.checking(new Expectations() {{
            one(service).close();
        }});

        registry.close();
    }

    @Test
    public void ignoresServiceWithNoCloseMethod() {
        registry.add(String.class, "service");

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
    }

    private interface TestService {
        void close();
    }
}
