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

import org.junit.Test;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TypeOfTest {

    @Test
    public void canRepresentGenericArrayType() {
        TypeOf<List<String[]>[]> type = new TypeOf<List<String[]>[]>() {};

        assertEquals(type.getConcreteClass(), List[].class);
        assertTrue(type.isArray());
        assertFalse(type.isSimple());
        assertFalse(type.isParameterized());

        assertEquals(
            new TypeOf<List<String[]>>() {},
            type.getComponentType());
    }

    @Test
    public void canRepresentPrimitiveArrayType() {
        TypeOf<String[]> type = new TypeOf<String[]>() {};

        assertEquals(type.getConcreteClass(), String[].class);
        assertTrue(type.isArray());
        assertFalse(type.isSimple());
        assertFalse(type.isParameterized());

        assertEquals(
            new TypeOf<String>() {},
            type.getComponentType());
    }

    @Test
    public void canRepresentParameterizedType() {
        TypeOf<List<String>> type = new TypeOf<List<String>>() {};

        assertTrue(type.isParameterized());
        assertFalse(type.isArray());
        assertFalse(type.isSimple());
        assertEquals(type.getConcreteClass(), List.class);

        assertEquals(
            new TypeOf<List>() {},
            type.getParameterizedTypeDefinition());
        assertEquals(
            type.getActualTypeArguments(),
            singletonList(new TypeOf<String>() {}));
    }

    @Test
    public void canRepresentSimpleType() {
        TypeOf<String> type = new TypeOf<String>() {};

        assertTrue(type.isSimple());
        assertFalse(type.isArray());
        assertFalse(type.isParameterized());
        assertEquals(type.getConcreteClass(), String.class);
    }

    @Test
    public void canRepresentWildcardTypeExpression() {
        TypeOf<?> type = new TypeOf<List<? extends Cloneable>>() {}.getActualTypeArguments().get(0);

        assertTrue(type.isWildcard());
        assertEquals(
            type.getUpperBound(),
            new TypeOf<Cloneable>() {});

        assertFalse(type.isSimple());
        assertFalse(type.isArray());
        assertFalse(type.isParameterized());
        assertEquals(type.getConcreteClass(), Cloneable.class);
    }

    @Test
    public void canRepresentWildcardTypeExpressionWithDefaultUpperBound() {
        TypeOf<?> type = new TypeOf<List<?>>() {}.getActualTypeArguments().get(0);

        assertTrue(type.isWildcard());
        assertNull(type.getUpperBound());

        assertFalse(type.isSimple());
        assertFalse(type.isArray());
        assertFalse(type.isParameterized());
        assertEquals(type.getConcreteClass(), Object.class);
    }
}
