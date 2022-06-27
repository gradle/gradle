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

package org.gradle.internal.upgrade.report;

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Copy;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
//        new ApiUpgradeHandler(ImmutableList.copyOf(replacements)).useInstance();
    }

    public ApiUpgrader createApiUpgrader() {            // It seems like the bytecode references the subclass as an owner
        matchProperty(
            "org.gradle.api.tasks.compile.AbstractCompile",
            String.class,
            "targetCompatibility",
            Collections.singletonList("org.gradle.api.tasks.compile.JavaCompile")
        ).replaceWith(abstractCompile -> abstractCompile, (abstractCompile, value) -> {});

        matchProperty(
            "org.gradle.api.tasks.compile.AbstractCompile",
            String.class,
            "sourceCompatibility",
            Collections.singletonList("org.gradle.api.tasks.compile.JavaCompile")
        ).replaceWith(abstractCompile -> abstractCompile, (abstractCompile, value) -> {});

        matchProperty(
            "org.gradle.api.tasks.compile.AbstractCompile",
            FileCollection.class,
            "classpath",
            ImmutableList.of("org.gradle.api.tasks.compile.JavaCompile",
                "org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile",
                "org.jetbrains.kotlin.gradle.tasks.KotlinCompile",
                "org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask",
                "org.gradle.api.tasks.compile.GroovyCompile")
        ).replaceWith(abstractCompile -> null, (abstractCompile, value) -> {});

        matchProperty(
            "org.gradle.api.tasks.compile.CompileOptions",
            List.class,
            "compilerArgs"
        ).replaceWith(abstractCompile -> null, (abstractCompile, value) -> {});

        matchProperty(
            Copy.class.getName(),
            File.class,
            "destinationDir",
            ImmutableList.of("org.gradle.language.jvm.tasks.ProcessResources")
        ).replaceWith(abstractCompile -> null, (abstractCompile, value) -> {});

        return new ApiUpgrader(replacements);
    }

    public interface MethodReplacer {
        <T> void replaceWith(ReplacementLogic<T> method);
    }

    public <T> MethodReplacer matchMethod(String type, Type returnType, String methodName, Type... argumentTypes) {
        return new MethodReplacer() {
            @Override
            public <R> void replaceWith(ReplacementLogic<R> replacement) {
                replacements.add(new MethodReplacement<>(type, Collections.emptyList(), returnType, methodName, argumentTypes, replacement));
            }
        };
    }

    public interface PropertyReplacer<T, P> {
        void replaceWith(Function<? super T, ? extends P> getter, BiConsumer<? super T, ? super P> setter);
    }

    public <T, P> PropertyReplacer<T, P> matchProperty(String type, Class<?> propertyType, String propertyName) {
        return matchProperty(type, propertyType, propertyName, Collections.emptyList());
    }
    public <T, P> PropertyReplacer<T, P> matchProperty(String type, Class<?> propertyType, String propertyName, Collection<String> additionalTypes) {
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
                addGetterReplacement(type, additionalTypes, propertyType, getterPrefix + capitalizedPropertyName, getterReplacement);
                addSetterReplacement(type, additionalTypes, propertyType, "set" + capitalizedPropertyName, setterReplacement);
                addGroovyPropertyReplacement(type, propertyName, getterReplacement, setterReplacement);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T, P> void addGetterReplacement(String type, Collection<String> additionalTypes, Class<?> propertyType, String getterName, Function<? super T, ? extends P> getterReplacement) {
        replacements.add(new MethodReplacement<P>(
            type,
            additionalTypes,
            Type.getType(propertyType),
            getterName,
            new Type[]{},
            (receiver, arguments) -> getterReplacement.apply((T) receiver)));
    }

    @SuppressWarnings("unchecked")
    private <T, P> void addSetterReplacement(String type, Collection<String> additionalTypes, Class<?> propertyType, String setterName, BiConsumer<? super T, ? super P> setterReplacement) {
        replacements.add(new MethodReplacement<Void>(
            type,
            additionalTypes,
            Type.VOID_TYPE,
            setterName,
            new Type[]{Type.getType(propertyType)},
            (receiver, arguments) -> {
                setterReplacement.accept((T) receiver, (P) arguments[0]);
                return null;
            }));
    }

    private <T, V> void addGroovyPropertyReplacement(String type, String propertyName, Function<? super T, ? extends V> getterReplacement, BiConsumer<? super T, ? super V> setterReplacement) {
//        replacements.add(new DynamicGroovyPropertyReplacement<>(type, propertyName, getterReplacement, setterReplacement));
    }
}
