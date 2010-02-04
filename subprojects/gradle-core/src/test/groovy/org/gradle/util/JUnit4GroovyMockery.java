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

/**
 * @author Hans Dockter
 */
import groovy.lang.Closure;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.hamcrest.Matcher;

import java.util.Arrays;
import java.util.List;

public class JUnit4GroovyMockery extends JUnit4Mockery {
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
                    Object result = cl.call(params.subList(0, Math.min(invocation.getParametersAsArray().length,
                            cl.getMaximumNumberOfParameters())));
                    if (invocation.getInvokedMethod().getReturnType().isInstance(result)) {
                        return result;
                    }
                    return null;                
                }
            });
        }
    }

    public void checking(Closure c) {
        ClosureExpectations expectations = new ClosureExpectations();
        expectations.closureInit(c, expectations);
        super.checking(expectations);
    }
}