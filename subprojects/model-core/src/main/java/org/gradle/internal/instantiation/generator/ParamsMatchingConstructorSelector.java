/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator;

import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.util.List;

class ParamsMatchingConstructorSelector implements ConstructorSelector {
    private final ClassGenerator classGenerator;

    public ParamsMatchingConstructorSelector(ClassGenerator classGenerator) {
        this.classGenerator = classGenerator;
    }

    @Override
    public void vetoParameters(ClassGenerator.GeneratedConstructor<?> constructor, Object[] parameters) {
        // Don't care
    }

    @Override
    public <T> ClassGenerator.GeneratedConstructor<? extends T> forType(Class<T> type) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("This constructor selector requires the construction parameters");
    }

    @Override
    public <T> ClassGenerator.GeneratedConstructor<? extends T> forParams(final Class<T> type, Object[] params) {
        ClassGenerator.GeneratedClass<?> generatedClass = classGenerator.generate(type);

        if (generatedClass.getOuterType() != null && (params.length == 0 || !generatedClass.getOuterType().isInstance(params[0]))) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is a non-static inner class.");
            throw new IllegalArgumentException(formatter.toString());
        }

        List<? extends ClassGenerator.GeneratedConstructor<?>> constructors = generatedClass.getConstructors();
        if (constructors.size() == 1) {
            return Cast.uncheckedCast(constructors.get(0));
        }

        ClassGenerator.GeneratedConstructor<?> match = null;
        for (ClassGenerator.GeneratedConstructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length < params.length) {
                continue;
            }
            int fromParam = 0;
            int toParam = 0;
            for (; fromParam < params.length; fromParam++) {
                Object param = params[fromParam];
                while (param != null && toParam < parameterTypes.length) {
                    Class<?> toType = parameterTypes[toParam];
                    if (toType.isPrimitive()) {
                        toType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(toType);
                    }
                    if (toType.isInstance(param)) {
                        break;
                    }
                    toParam++;
                }
                if (toParam == parameterTypes.length) {
                    break;
                }
                toParam++;
            }
            if (fromParam == params.length) {
                if (match == null || parameterTypes.length < match.getParameterTypes().length) {
                    // Choose the shortest match
                    match = constructor;
                } else if (parameterTypes.length == match.getParameterTypes().length) {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("Multiple constructors of type ");
                    formatter.appendType(type);
                    formatter.append(" match parameters: ");
                    formatter.appendValues(params);
                    throw new IllegalArgumentException(formatter.toString());
                }
            }
        }
        if (match != null) {
            return Cast.uncheckedCast(match);
        }

        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No constructors of type ");
        formatter.appendType(type);
        formatter.append(" match parameters: ");
        formatter.appendValues(params);
        throw new IllegalArgumentException(formatter.toString());
    }
}
