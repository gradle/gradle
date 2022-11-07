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
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import org.gradle.internal.reflect.PropertyAccessorType;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toSet;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.gradlePublicApi;

@AnalyzeClasses(packages = "org.gradle")
public class KotlinCompatibilityTest {

    @ArchTest
    public static final ArchRule consistent_nullable_annotations_on_public_api = freeze(classes().that(are(gradlePublicApi())).should(haveAccessorsWithSymmetricalNullableAnnotations()));

    @ArchTest
    public static final ArchRule consistent_nullable_annotations_on_internal_api = freeze(classes().that(are(not(gradlePublicApi()))).should(haveAccessorsWithSymmetricalNullableAnnotations()));

    private static ArchCondition<JavaClass> haveAccessorsWithSymmetricalNullableAnnotations() {
        return new ArchCondition<JavaClass>("have accessors with symmetrical @Nullable annotations") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                findMutableProperties(item).stream()
                    .filter(accessors -> !accessors.nullableIsSymmetric())
                    .map(accessor -> violated(item, String.format("Accessors for %s don't use symmetrical @Nullable", accessor)))
                    .forEach(events::add);
            }

            private Set<Accessors> findMutableProperties(JavaClass javaClass) {
                Map<String, List<Accessor>> accessors = findAccessors(javaClass.getMethods());
                return accessors.entrySet().stream().map(entry -> Accessors.from(entry.getKey(), entry.getValue())).filter(Objects::nonNull).collect(toSet());
            }
        };
    }

    private static Map<String, List<Accessor>> findAccessors(Set<JavaMethod> methods) {
        return methods.stream().map(Accessor::from).filter(Objects::nonNull).collect(groupingBy(Accessor::getPropertyName));
    }

    private static class Accessor {
        private final PropertyAccessorType accessorType;
        private final JavaMethod method;
        private final boolean isGetter;

        @Nullable
        public static Accessor from(JavaMethod method) {
            PropertyAccessorType propertyAccessorType = PropertyAccessorType.fromName(method.getName());
            if (propertyAccessorType != null && (KotlinCompatibilityTest.isGetter(method, propertyAccessorType) || KotlinCompatibilityTest.isSetter(method, propertyAccessorType))) {
                return new Accessor(propertyAccessorType, method);
            }
            return null;
        }

        private Accessor(PropertyAccessorType accessorType, JavaMethod method) {
            this.accessorType = accessorType;
            this.method = method;
            this.isGetter = accessorType == PropertyAccessorType.IS_GETTER || accessorType == PropertyAccessorType.GET_GETTER;
        }

        public String getPropertyName() {
            return accessorType.propertyNameFor(method.getName());
        }

        public JavaMethod getMethod() {
            return method;
        }

        public boolean isGetter() {
            return isGetter;
        }

        public boolean isSetter() {
            return !isGetter;
        }
    }

    private static class Accessors {
        private final JavaClass owningClass;
        private final String propertyName;
        private final Set<JavaMethod> getters;
        private final Set<JavaMethod> setters;

        @Nullable
        public static Accessors from(String propertyName, List<Accessor> accessors) {
            Map<Boolean, List<Accessor>> gettersAndSetters = accessors.stream().collect(partitioningBy(Accessor::isGetter));
            JavaClass owningClass = accessors.iterator().next().getMethod().getOwner();
            Set<JavaMethod> getters = gettersAndSetters.get(Boolean.TRUE).stream().map(Accessor::getMethod).collect(toSet());
            Set<JavaMethod> setters = gettersAndSetters.get(Boolean.FALSE).stream().map(Accessor::getMethod).collect(toSet());
            if (!getters.isEmpty() && !setters.isEmpty()) {
                return new Accessors(owningClass, propertyName, getters, setters);
            }
            List<Accessor> superclassAccessors = findAccessors(owningClass.getAllMethods()).get(propertyName);
            if (superclassAccessors == null) {
                return null;
            }
            if (!setters.isEmpty()) {
                Set<JavaMethod> superclassGetters = superclassAccessors.stream().filter(Accessor::isGetter).map(Accessor::getMethod).collect(toSet());
                if (!superclassGetters.isEmpty()) {
                    return new Accessors(owningClass, propertyName, superclassGetters, setters);
                }
            } else {
                Set<JavaMethod> superclassSetters = superclassAccessors.stream().filter(Accessor::isSetter).map(Accessor::getMethod).collect(toSet());
                if (!superclassSetters.isEmpty()) {
                    return new Accessors(owningClass, propertyName, getters, superclassSetters);
                }
            }
            return null;
        }

        private Accessors(JavaClass owningClass, String propertyName, Set<JavaMethod> getters, Set<JavaMethod> setters) {
            this.getters = getters;
            this.setters = setters;
            this.owningClass = owningClass;
            this.propertyName = propertyName;
        }

        public boolean nullableIsSymmetric() {
            long nullableGetterCount = getters.stream().filter(this::getterAnnotatedWithNullable).count();
            long nullableSetterCount = setters.stream().filter(this::setterAnnotatedWithNullable).count();

            boolean gettersArePartiallyNull = 0 < nullableGetterCount && nullableGetterCount < getters.size();
            boolean settersArePartiallyNull = 0 < nullableSetterCount && nullableSetterCount < setters.size();
            if (gettersArePartiallyNull || settersArePartiallyNull) {
                return false;
            }

            boolean nonNullGetters = nullableGetterCount == 0;
            boolean nonNullSetters = nullableSetterCount == 0;
            // Either both are non-null, or both are nullable
            return nonNullGetters == nonNullSetters;
        }

        private boolean getterAnnotatedWithNullable(JavaMethod getter) {
            return getter.isAnnotatedWith(Nullable.class);
        }

        private boolean setterAnnotatedWithNullable(JavaMethod setter) {
            return Arrays.stream(setter.reflect().getParameterAnnotations()[0]).anyMatch(a -> a instanceof Nullable);
        }

        @Override
        public String toString() {
            return owningClass.getName() + "." + propertyName;
        }
    }

    private static boolean isGetter(JavaMethod m, PropertyAccessorType accessorType) {
        // We avoid using reflect, since that leads to class loading exceptions
        return !m.getModifiers().contains(JavaModifier.STATIC)
            && (accessorType == PropertyAccessorType.GET_GETTER || accessorType == PropertyAccessorType.IS_GETTER)
            && m.getRawParameterTypes().size() == 0
            && (accessorType != PropertyAccessorType.IS_GETTER || m.getRawReturnType().isEquivalentTo(Boolean.TYPE) || m.getRawReturnType().isEquivalentTo(Boolean.class));
    }

    private static boolean isSetter(JavaMethod m, PropertyAccessorType accessorType) {
        return !m.getModifiers().contains(JavaModifier.STATIC)
            && accessorType == PropertyAccessorType.SETTER
            && m.getRawParameterTypes().size() == 1;
    }
}
