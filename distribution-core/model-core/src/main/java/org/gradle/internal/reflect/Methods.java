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
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.lang.reflect.Method;
import java.util.Arrays;

@SuppressWarnings("NullableProblems")
public class Methods {
    /**
     * Equivalence of methods based on method name, and the number, type and order of parameters. Return types must be compatible.
     */
    public static final Equivalence<Method> SIGNATURE_EQUIVALENCE = new Equivalence<Method>() {
        @Override
        protected boolean doEquivalent(Method a, Method b) {
            if (a.getName().equals(b.getName())) {
                if (a.getReturnType().equals(b.getReturnType())
                    || (a.getReturnType().isAssignableFrom(b.getReturnType())
                    || b.getReturnType().isAssignableFrom(a.getReturnType()))) {
                    return Arrays.equals(a.getGenericParameterTypes(), b.getGenericParameterTypes());
                }
            }
            return false;
        }

        @Override
        protected int doHash(Method method) {
            return new HashCodeBuilder()
                .append(method.getName())
                .append(method.getParameterTypes())
                .toHashCode();
        }
    };

    /**
     * Equivalence of methods based on method name, the number, type and order of parameters, and return types.
     */
    public static final Equivalence<Method> DESCRIPTOR_EQUIVALENCE = new Equivalence<Method>() {
        @Override
        protected boolean doEquivalent(Method a, Method b) {
            if (a.getName().equals(b.getName())) {
                return a.getGenericReturnType().equals(b.getGenericReturnType())
                    && Arrays.equals(a.getGenericParameterTypes(), b.getGenericParameterTypes());
            }
            return false;
        }

        @Override
        protected int doHash(Method method) {
            return new HashCodeBuilder()
                .append(method.getName())
                .append(method.getParameterTypes())
                .append(method.getReturnType())
                .toHashCode();
        }
    };
}
