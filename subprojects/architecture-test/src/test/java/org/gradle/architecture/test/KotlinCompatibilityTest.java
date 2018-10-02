/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.architecture.test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.toSet;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.gradle")
public class KotlinCompatibilityTest {

    @ArchTest
    public static final ArchRule consistent_nullable_annotations = classes().should(haveAccessorsWithSymmetricalNullableAnnotations());

    private static ArchCondition<JavaClass> haveAccessorsWithSymmetricalNullableAnnotations() {
        return new ArchCondition<JavaClass>("have accessors with symmetrical @Nullable annotations") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                findSymmetricalAccessors(item).stream()
                    .filter(this::nullableIsUnsymmetrical)
                    .map(accessor -> violated(item, String.format("Accessors for %s don't use symmetrical @Nullable", accessor)))
                    .forEach(events::add);
            }

            private Set<Accessors> findSymmetricalAccessors(JavaClass javaClass) {
                return filterGetters(javaClass.getMethods()).stream()
                    .flatMap(getter -> tryFindMatchingSetters(javaClass, getter).map(setter -> new Accessors(getter, setter)))
                    .collect(toSet());
            }

            private Set<JavaMethod> filterGetters(Set<JavaMethod> methods) {
                return methods.stream().filter(KotlinCompatibilityTest::isGetter).collect(toSet());
            }

            private Stream<JavaMethod> tryFindMatchingSetters(JavaClass javaClass, JavaMethod getter) {
                String propertyName = PropertyAccessorType.fromName(getter.getName()).propertyNameFor(getter.getName());
                return javaClass.getMethods().stream().filter(m -> isSetterFor(propertyName, m));
            }

            private boolean nullableIsUnsymmetrical(Accessors accessor) {
                boolean getterIsNullable = accessor.getter.isAnnotatedWith(Nullable.class);
                boolean setterIsNullable = firstParameterIsAnnotatedWithNullable(accessor.setter);
                return (getterIsNullable && !setterIsNullable) || (!getterIsNullable && setterIsNullable);
            }

            private boolean firstParameterIsAnnotatedWithNullable(JavaMethod setter) {
                return Arrays.stream(setter.reflect().getParameterAnnotations()[0]).anyMatch(a -> a instanceof Nullable);
            }
        };
    }

    private static boolean isGetter(JavaMethod m) {
        // We avoid using reflect, since that leads to class loading exceptions
        PropertyAccessorType accessorType = PropertyAccessorType.fromName(m.getName());
        return !m.getModifiers().contains(JavaModifier.STATIC)
            && (accessorType == PropertyAccessorType.GET_GETTER || accessorType == PropertyAccessorType.IS_GETTER)
            && m.getParameters().size() == 0
            && (accessorType != PropertyAccessorType.IS_GETTER || m.getReturnType().reflect().equals(Boolean.TYPE) || m.getReturnType().reflect().equals(Boolean.class));
    }

    private static boolean isSetterFor(String propertyName, JavaMethod m) {
        // We avoid using reflect, since that leads to class loading exceptions
        PropertyAccessorType accessorType = PropertyAccessorType.fromName(m.getName());
        return !m.getModifiers().contains(JavaModifier.STATIC)
            && accessorType == PropertyAccessorType.SETTER
            && m.getParameters().size() == 1
            && accessorType.propertyNameFor(m.getName()).equals(propertyName);
    }

    private static class Accessors {
        private final JavaMethod getter;
        private final JavaMethod setter;

        private Accessors(JavaMethod getter, JavaMethod setter) {
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        public String toString() {
            return getter.getOwner().getName() + "." + propertyNameOf(getter) + ": " + setter.getParameters().get(0).getSimpleName();
        }

        private String propertyNameOf(JavaMethod getter) {
            return PropertyAccessorType.fromName(getter.getName()).propertyNameFor(getter.getName());
        }
    }
}
