/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ApiUpgradeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiUpgradeManager.class);

    private static final Type[] EMPTY = {};

    private final List<Replacement> replacements = new ArrayList<>();

    public void init() {
        for (Replacement replacement : replacements) {
            replacement.initializeReplacement();
        }
        new ApiUpgradeHandler(ImmutableList.copyOf(replacements)).useInstance();
    }

    public ApiUpgrader createApiUpgrader() {
        return new ApiUpgrader(replacements);
    }

    public interface MethodReplacer {
        <T> void replaceWith(ReplacementLogic<T> method);
    }

    public MethodReplacer matchMethod(Type type, Type returnType, String methodName, Type... argumentTypes) {
        return new MethodReplacer() {
            @Override
            public <T> void replaceWith(ReplacementLogic<T> replacement) {
                replacements.add(new MethodReplacement<>(type, returnType, methodName, argumentTypes, replacement));
            }
        };
    }

    public interface PropertyReplacer<T, P> {
        void replaceWith(Function<? super T, ? extends P> getter, BiConsumer<? super T, ? super P> setter);
    }

    public <T, P> PropertyReplacer<T, P> matchProperty(Class<T> type, Class<P> propertyType, String propertyName) {
        return new PropertyReplacer<T, P>() {
            @Override
            public void replaceWith(
                Function<? super T, ? extends P> getterReplacement,
                BiConsumer<? super T, ? super P> setterReplacement
            ) {
                String capitalizedPropertyName = propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
                String getterPrefix = propertyType.equals(boolean.class)
                    ? "is"
                    : "get";
                addGetterReplacement(type, propertyType, getterPrefix + capitalizedPropertyName, getterReplacement);
                addSetterReplacement(type, propertyType, "set" + capitalizedPropertyName, setterReplacement);
                addGroovyPropertyReplacement(type, propertyName, getterReplacement, setterReplacement);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T, P> void addGetterReplacement(Class<T> type, Class<P> propertyType, String getterName, Function<? super T, ? extends P> getterReplacement) {
        replacements.add(new MethodReplacement<P>(
            Type.getType(type),
            Type.getType(propertyType),
            getterName,
            new Type[]{},
            (receiver, arguments) -> getterReplacement.apply((T) receiver)));
    }

    @SuppressWarnings("unchecked")
    private <T, P> void addSetterReplacement(Class<T> type, Class<P> propertyType, String setterName, BiConsumer<? super T, ? super P> setterReplacement) {
        replacements.add(new MethodReplacement<Void>(
            Type.getType(type),
            Type.VOID_TYPE,
            setterName,
            new Type[]{Type.getType(propertyType)},
            (receiver, arguments) -> {
                setterReplacement.accept((T) receiver, (P) arguments[0]);
                return null;
            }));
    }

    private <T, V> void addGroovyPropertyReplacement(Class<T> type, String propertyName, Function<? super T, ? extends V> getterReplacement, BiConsumer<? super T, ? super V> setterReplacement) {
        replacements.add(new DynamicGroovyPropertyReplacement<>(type, propertyName, getterReplacement, setterReplacement));
    }
}
