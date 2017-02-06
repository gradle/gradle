/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.reflect;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static java.util.Arrays.asList;

@RunWith(JMock.class)
public class TypeOfVisitorTest {

    private final JUnit4Mockery mockery = new JUnit4Mockery();

    @Test
    public void acceptsGenericArrayVisitor() {
        final TypeOf.Visitor visitor = visitor();

        checking(new Expectations() {{
            oneOf(visitor).visitArrayOf(new TypeOf<List<String[]>>() {});
        }});

        new TypeOf<List<String[]>[]>() {}.accept(visitor);
    }

    @Test
    public void acceptsPrimitiveArrayVisitor() {
        final TypeOf.Visitor visitor = visitor();

        checking(new Expectations() {{
            oneOf(visitor).visitArrayOf(new TypeOf<String>() {});
        }});

        new TypeOf<String[]>() {}.accept(visitor);
    }

    @Test
    public void acceptsParameterizedTypeVisitor() {
        final TypeOf.Visitor visitor = visitor();

        checking(new Expectations() {{
            oneOf(visitor).visitParameterized(new TypeOf<List>() {}, listOf(new TypeOf<String>() {}));
        }});

        new TypeOf<List<String>>() {}.accept(visitor);
    }

    @Test
    public void acceptsSimple() {
        final TypeOf.Visitor visitor = visitor();

        checking(new Expectations() {{
            oneOf(visitor).visitSimple(new TypeOf<String>() {});
        }});

        new TypeOf<String>() {}.accept(visitor);
    }

    private List<TypeOf<?>> listOf(TypeOf<?>... types) {
        return asList(types);
    }

    private void checking(Expectations expectations) {
        mockery.checking(expectations);
    }

    private TypeOf.Visitor visitor() {
        return mockery.mock(TypeOf.Visitor.class);
    }
}
