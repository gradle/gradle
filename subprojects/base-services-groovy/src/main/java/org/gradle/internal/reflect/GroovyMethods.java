/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.reflect;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import groovy.lang.GroovyObject;
import org.gradle.api.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.gradle.internal.reflect.Methods.SIGNATURE_EQUIVALENCE;

public class GroovyMethods {

    private static final Set<Equivalence.Wrapper<Method>> OBJECT_METHODS = ImmutableSet.copyOf(
        Iterables.transform(
            Iterables.concat(
                Arrays.asList(Object.class.getMethods()),
                Arrays.asList(GroovyObject.class.getMethods())
            ), new Function<Method, Equivalence.Wrapper<Method>>() {
                public Equivalence.Wrapper<Method> apply(@Nullable Method input) {
                    return SIGNATURE_EQUIVALENCE.wrap(input);
                }
            }
        )
    );

    /**
     * Is defined by Object or GroovyObject?
     */
    public static boolean isObjectMethod(Method method) {
        return OBJECT_METHODS.contains(SIGNATURE_EQUIVALENCE.wrap(method));
    }
}
