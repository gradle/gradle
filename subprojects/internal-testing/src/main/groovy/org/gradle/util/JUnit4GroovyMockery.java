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

package org.gradle.util;


import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JUnit4GroovyMockery extends JUnit4Mockery {
    private final ConcurrentMap<String, AtomicInteger> names = new ConcurrentHashMap<String, AtomicInteger>();

    public JUnit4GroovyMockery() {
        setImposteriser(ClassImposteriser.INSTANCE);
    }

    @Override
    public <T> T mock(Class<T> typeToMock) {
        return mock(typeToMock, typeToMock.getSimpleName());
    }

    @Override
    public <T> T mock(Class<T> typeToMock, String name) {
        names.putIfAbsent(name, new AtomicInteger());
        int count = names.get(name).getAndIncrement();
        T mock;
        if (count == 0) {
            mock = super.mock(typeToMock, name);
        } else {
            mock = super.mock(typeToMock, name + count);
        }
        if (mock instanceof ClassLoader) {
            for (Field field : ClassLoader.class.getDeclaredFields()) {
                if (field.getName().equals("initialized")) {
                    field.setAccessible(true);
                    try {
                        field.set(mock, true);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                    break;
                }
            }
        }
        return mock;
    }

    class ClosureExpectations extends Expectations {
        void closureInit(Closure cl, Object delegate) {
            cl.setDelegate(delegate);
            cl.call();
        }

        <T> void withParam(Matcher<T> matcher) {
            this.with(matcher);
        }

        void will(final Closure cl) {
            will(new Action() {
                public void describeTo(Description description) {
                    description.appendText("execute closure");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    List<Object> params = Arrays.asList(invocation.getParametersAsArray());
                    Object result;
                    try {
                        List<Object> subParams = params.subList(0, Math.min(invocation.getParametersAsArray().length,
                                cl.getMaximumNumberOfParameters()));
                        result = cl.call(subParams.toArray(new Object[0]));
                    } catch (InvokerInvocationException e) {
                        throw e.getCause();
                    }
                    if (invocation.getInvokedMethod().getReturnType().isInstance(result)) {
                        return result;
                    }
                    return null;
                }
            });
        }
    }

    public void checking(@DelegatesTo(type = "org.gradle.util.JUnit4GroovyMockery.ClosureExpectations") Closure c) {
        ClosureExpectations expectations = new ClosureExpectations();
        expectations.closureInit(c, expectations);
        super.checking(expectations);
    }
}
