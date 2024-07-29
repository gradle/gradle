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
import org.gradle.model.internal.asm.AsmClassGeneratorUtils;

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
        // NOTE: this relies on the constructors being in a predictable order
        // sorted by the number of parameters the constructor requires
        for (ClassGenerator.GeneratedConstructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            // The candidate constructor has fewer parameters than the number of
            // parameters we were given. This can't be the constructor
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
                        toType = AsmClassGeneratorUtils.getWrapperTypeForPrimitiveType(toType);
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
            // The current candidate's parameter types matched the given parameter types
            // There may be more parameters to the constructor than were given because
            // we can inject additional services
            if (fromParam == params.length) {
                if (match == null) {
                    // We had no previous match, so choose this candidate
                    match = constructor;
                } else if (parameterTypes.length < match.getParameterTypes().length) {
                    // We had a previous match, if this candidate has fewer parameters, choose it as the best match
                    match = constructor;
                } else if (parameterTypes.length == match.getParameterTypes().length) {
                    // We have a previous match with the same number of parameters as this candidate.
                    // This means for the given parameters, we've found two constructors that could be used
                    // Given constructors C1 and C2.
                    // C1(A, B, C, D)
                    // C2(A, B, X, D)
                    // C1 and C2 both accept the parameters A, B and D, but
                    // C1 accepts a parameter of type C at position 2 and
                    // C2 accepts a parameter of type X at position 2.
                    // This will trigger this check because we cannot tell which is the "better"
                    // constructor assuming that the other parameter could be injected.

                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("Multiple constructors for parameters ");
                    formatter.appendValues(params);
                    formatter.startNumberedChildren();
                    formatter.node("candidate: ");
                    formatter.appendType(type);
                    formatter.appendTypes(parameterTypes);
                    formatter.node("best match: ");
                    formatter.appendType(type);
                    formatter.appendTypes(match.getParameterTypes());
                    formatter.endChildren();
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
