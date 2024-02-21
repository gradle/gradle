/*
 * Copyright 2021 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaGenericArrayType;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.domain.PackageMatchers;
import com.tngtech.archunit.core.domain.properties.HasType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.gradle.test.precondition.Requires;
import org.gradle.test.precondition.TestPrecondition;
import org.gradle.util.EmptyStatement;
import org.gradle.util.Matchers;
import org.gradle.util.SetSystemProperties;
import org.gradle.util.TestClassLoader;
import org.gradle.util.UsesNativeServices;
import org.gradle.util.UsesNativeServicesExtension;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.base.DescribedPredicate.equalTo;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.core.domain.JavaMember.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;
import static com.tngtech.archunit.core.domain.properties.HasModifiers.Predicates.modifier;
import static com.tngtech.archunit.core.domain.properties.HasName.Functions.GET_NAME;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasType.Functions.GET_RAW_TYPE;
import static java.util.stream.Collectors.toSet;

public interface ArchUnitFixture {
    DescribedPredicate<JavaClass> classes_not_written_in_kotlin = resideOutsideOfPackages("org.gradle.configurationcache..", "org.gradle.kotlin..")
        .as("classes written in Java or Groovy");

    DescribedPredicate<JavaClass> not_synthetic_classes = new DescribedPredicate<JavaClass>("not synthetic classes") {
        @Override
        public boolean test(JavaClass javaClass) {
            return !javaClass.getModifiers().contains(JavaModifier.SYNTHETIC);
        }
    };

    DescribedPredicate<JavaMember> not_written_in_kotlin = declaredIn(classes_not_written_in_kotlin)
        .as("written in Java or Groovy");

    DescribedPredicate<JavaMember> kotlin_internal_methods = declaredIn(gradlePublicApi())
        .and(not(not_written_in_kotlin))
        .and(modifier(PUBLIC))
        .and(nameMatching(".+\\$[a-z_]+")) // Kotlin internal methods have `$kotlin_module_name` appended to their name
        .as("Kotlin internal methods");

    DescribedPredicate<JavaMember> public_api_methods = declaredIn(gradlePublicApi())
        .and(modifier(PUBLIC))
        .and(not(kotlin_internal_methods))
        .as("public API methods");

    static ArchRule freeze(ArchRule rule) {
        return new FreezeInstructionsPrintingArchRule(FreezingArchRule.freeze(rule));
    }

    static DescribedPredicate<JavaClass> gradlePublicApi() {
        return new GradlePublicApi();
    }

    static DescribedPredicate<JavaClass> gradleInternalApi() {
        return resideInAnyPackage("org.gradle..")
            .and(not(gradlePublicApi()))
            .as("Gradle Internal API");
    }

    static DescribedPredicate<JavaClass> inGradlePublicApiPackages() {
        return new InGradlePublicApiPackages();
    }

    static DescribedPredicate<JavaClass> inTestFixturePackages() {
        return resideInAnyPackage("org.gradle.test.fixtures..", "org.gradle.integtests.fixtures..", "org.gradle.architecture.test..")
            .as("in test fixture packages");
    }

    static DescribedPredicate<JavaClass> inGradleInternalApiPackages() {
        return resideInAnyPackage("org.gradle..")
            .and(not(inGradlePublicApiPackages()))
            .and(not(inTestFixturePackages()))
            .as("in Gradle internal API packages");
    }

    DescribedPredicate<JavaClass> primitive = new DescribedPredicate<JavaClass>("primitive") {
        @Override
        public boolean test(JavaClass input) {
            return input.isPrimitive();
        }
    };

    static <T> DescribedPredicate<Collection<T>> thatAll(DescribedPredicate<T> predicate) {
        return new DescribedPredicate<Collection<T>>("that all %s", predicate.getDescription()) {
            @Override
            public boolean test(Collection<T> input) {
                return input.stream().allMatch(predicate);
            }
        };
    }

    static ArchCondition<JavaClass> beAbstract() {
        return new ArchCondition<JavaClass>("be abstract") {
            @Override
            public void check(JavaClass input, ConditionEvents events) {
                if (input.isInterface() || input.getModifiers().contains(JavaModifier.ABSTRACT)) {
                    events.add(new SimpleConditionEvent(input, true, input.getFullName() + " is abstract"));
                } else {
                    events.add(new SimpleConditionEvent(input, false, input.getFullName() + " is not abstract"));
                }
            }
        };
    }

    static ArchCondition<JavaClass> haveDirectSuperclassOrInterfaceThatAre(DescribedPredicate<JavaClass> types) {
        return new HaveDirectSuperclassOrInterfaceThatAre(types);
    }

    static ArchCondition<JavaMethod> haveOnlyArgumentsOrReturnTypesThatAre(DescribedPredicate<JavaClass> types) {
        return new HaveOnlyArgumentsOrReturnTypesThatAre(types);
    }

    static ArchCondition<JavaClass> overrideMethod(String name, Class<?>[] parameterTypes, Class<?> from) {
        return new ArchCondition<JavaClass>(" override method " + getMethodDescription(name, parameterTypes)) {

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional<JavaMethod> method = getMethodRecursively(javaClass);
                if (method.isPresent()) {
                    JavaClass sourceClass = method.get().getSourceCodeLocation().getSourceClass();
                    if (!sourceClass.getFullName().equals(from.getName())) {
                        return;
                    }
                }
                events.add(new SimpleConditionEvent(javaClass, false, javaClass.getFullName() + " doesn't override default method " + getMethodDescription(name, parameterTypes)));
            }

            private Optional<JavaMethod> getMethodRecursively(JavaClass javaClass) {
                while (true) {
                    Optional<JavaMethod> method = javaClass.tryGetMethod(name, parameterTypes);
                    if (method.isPresent()) {
                        return method;
                    }

                    Optional<JavaClass> superclass = javaClass.getRawSuperclass();
                    if (superclass.isPresent()) {
                        javaClass = superclass.get();
                    } else {
                        return Optional.empty();
                    }
                }
            }
        };
    }

    static String getMethodDescription(String name, Class<?>[] parameterTypes) {
        return name + "(" + Arrays.stream(parameterTypes).map(Class::getSimpleName).collect(Collectors.joining()) + ")";
    }

    static ArchCondition<JavaMethod> useJavaxAnnotationNullable() {
        return new ArchCondition<JavaMethod>("use javax.annotation.Nullable") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                // Check if method return type is annotated with the wrong Nullable
                if (!method.isAnnotatedWith(Nullable.class)) {
                    Set<JavaAnnotation<JavaMethod>> annotations = method.getAnnotations();
                    Set<JavaAnnotation<JavaMethod>> nullableAnnotations = extractPossibleNullable(annotations);
                    if (!nullableAnnotations.isEmpty()) {
                        events.add(new SimpleConditionEvent(method, false, method.getFullName() + " is using forbidden Nullable annotations: " + nullableAnnotations.stream().map(a -> a.getType().getName()).collect(Collectors.joining(","))));
                    } else {
                        events.add(new SimpleConditionEvent(method, true, method.getFullName() + " is not using forbidden Nullable"));
                    }
                }

                // Check if the method's parameters are annotated with the wrong Nullable
                for (JavaParameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotatedWith(Nullable.class)) {
                        Set<JavaAnnotation<JavaParameter>> annotations = parameter.getAnnotations();
                        Set<JavaAnnotation<JavaParameter>> nullableAnnotations = extractPossibleNullable(annotations);
                        if (!nullableAnnotations.isEmpty()) {
                            events.add(new SimpleConditionEvent(method, false, "parameter " + parameter.getIndex() + " for " + method.getFullName() + " is using forbidden Nullable annotations: " + nullableAnnotations.stream().map(a -> a.getType().getName()).collect(Collectors.joining(","))));
                        } else {
                            events.add(new SimpleConditionEvent(method, true, method.getFullName() + " is not using forbidden Nullable"));
                        }
                    }
                }
            }

            private <T extends HasDescription> Set<JavaAnnotation<T>> extractPossibleNullable(Set<JavaAnnotation<T>> annotations) {
                return annotations.stream().filter(annotation -> annotation.getType().getName().endsWith("Nullable")).collect(toSet());
            }
        };
    }

    static DescribedPredicate<JavaMember> annotatedMaybeInSupertypeWith(final Class<? extends Annotation> annotationType) {
        return annotatedMaybeInSupertypeWith(annotationType.getName());
    }

    static DescribedPredicate<JavaMember> annotatedMaybeInSupertypeWith(final String annotationTypeName) {
        DescribedPredicate<HasType> typeNameMatches = GET_RAW_TYPE.then(GET_NAME).is(equalTo(annotationTypeName));
        return annotatedMaybeInSupertypeWith(typeNameMatches.as("@" + annotationTypeName));
    }

    static DescribedPredicate<JavaMember> annotatedMaybeInSupertypeWith(final DescribedPredicate<? super JavaAnnotation<?>> predicate) {
        return new AnnotatedMaybeInSupertypePredicate(predicate);
    }

    static ArchCondition<JavaClass> beAnnotatedOrInPackageAnnotatedWith(Class<? extends Annotation> annotationType) {
        return ArchConditions.be(annotatedOrInPackageAnnotatedWith(annotationType));
    }

    /**
     * Either the class is directly annotated with the given annotation type or the class is in a package that is annotated with the given annotation type.
     */
    static DescribedPredicate<JavaClass> annotatedOrInPackageAnnotatedWith(Class<? extends Annotation> annotationType) {
        return new AnnotatedOrInPackageAnnotatedPredicate(annotationType);
    }

    class HaveOnlyArgumentsOrReturnTypesThatAre extends ArchCondition<JavaMethod> {
        private final DescribedPredicate<JavaClass> types;

        public HaveOnlyArgumentsOrReturnTypesThatAre(DescribedPredicate<JavaClass> types) {
            super("have only arguments or return types that are %s", types.getDescription());
            this.types = types;
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            Set<JavaClass> referencedTypes = new LinkedHashSet<>();
            unpackJavaType(method.getReturnType(), referencedTypes);
            method.getTypeParameters().forEach(typeParameter -> unpackJavaType(typeParameter, referencedTypes));
            referencedTypes.addAll(method.getRawParameterTypes());
            ImmutableSet<String> matchedClasses = referencedTypes.stream()
                .filter(it -> !types.test(it))
                .map(JavaClass::getName)
                .collect(ImmutableSet.toImmutableSet());
            boolean fulfilled = matchedClasses.isEmpty();
            String message = fulfilled
                ? String.format("%s has only arguments/return type that are %s in %s",
                method.getDescription(),
                types.getDescription(),
                method.getSourceCodeLocation())

                : String.format("%s has arguments/return type %s that %s not %s in %s",
                method.getDescription(),
                String.join(", ", matchedClasses),
                matchedClasses.size() == 1 ? "is" : "are",
                types.getDescription(),
                method.getSourceCodeLocation()
            );
            events.add(new SimpleConditionEvent(method, fulfilled, message));
        }

        private void unpackJavaType(JavaType type, Set<JavaClass> referencedTypes) {
            unpackJavaType(type, referencedTypes, new HashSet<>());
        }

        private void unpackJavaType(JavaType type, Set<JavaClass> referencedTypes, Set<JavaType> visited) {
            if (!visited.add(type)) {
                return;
            }
            if (type.toErasure().isEquivalentTo(Object.class)) {
                return;
            }
            referencedTypes.add(type.toErasure());
            if (type instanceof JavaTypeVariable) {
                List<JavaType> upperBounds = ((JavaTypeVariable<?>) type).getUpperBounds();
                upperBounds.forEach(bound -> unpackJavaType(bound, referencedTypes, visited));
            } else if (type instanceof JavaGenericArrayType) {
                unpackJavaType(((JavaGenericArrayType) type).getComponentType(), referencedTypes, visited);
            } else if (type instanceof JavaWildcardType) {
                JavaWildcardType wildcardType = (JavaWildcardType) type;
                wildcardType.getUpperBounds().forEach(bound -> unpackJavaType(bound, referencedTypes, visited));
                wildcardType.getLowerBounds().forEach(bound -> unpackJavaType(bound, referencedTypes, visited));
            } else if (type instanceof JavaParameterizedType) {
                ((JavaParameterizedType) type).getActualTypeArguments().forEach(argument -> unpackJavaType(argument, referencedTypes, visited));
            }
        }
    }

    class InGradlePublicApiPackages extends DescribedPredicate<JavaClass> {
        private static final PackageMatchers INCLUDES = PackageMatchers.of(parsePackageMatcher(System.getProperty("org.gradle.public.api.includes")));
        private static final PackageMatchers EXCLUDES = PackageMatchers.of(parsePackageMatcher(System.getProperty("org.gradle.public.api.excludes")));

        public InGradlePublicApiPackages() {
            super("in Gradle public API packages");
        }

        @Override
        public boolean test(JavaClass input) {
            return INCLUDES.test(input.getPackageName()) && !EXCLUDES.test(input.getPackageName());
        }

        private static Set<String> parsePackageMatcher(String packageList) {
            return Arrays.stream(packageList.split(":"))
                .map(include -> include.replace("**/", "..").replace("/**", "..").replace("/*", "").replace("/", "."))
                .collect(toSet());
        }
    }

    class GradlePublicApi extends DescribedPredicate<JavaClass> {
        private static final DescribedPredicate<JavaClass> TEST_FIXTURES = JavaClass.Predicates.belongToAnyOf(EmptyStatement.class, Matchers.class, Requires.class, SetSystemProperties.class, TestClassLoader.class, TestPrecondition.class, UsesNativeServices.class, UsesNativeServicesExtension.class);

        private final InGradlePublicApiPackages packages = new InGradlePublicApiPackages();

        public GradlePublicApi() {
            super("Gradle public API");
        }

        @Override
        public boolean test(JavaClass input) {
            return packages.test(input) && !TEST_FIXTURES.test(input) && input.getModifiers().contains(JavaModifier.PUBLIC);
        }
    }

    class HaveDirectSuperclassOrInterfaceThatAre extends ArchCondition<JavaClass> {
        private final DescribedPredicate<JavaClass> types;

        public HaveDirectSuperclassOrInterfaceThatAre(DescribedPredicate<JavaClass> types) {
            super("have direct super-class or interface that are %s", types.getDescription());
            this.types = types;
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            Optional<JavaClass> matchingSuperclass = item.getRawSuperclass()
                .filter(types);
            Stream<JavaClass> matchingInterfaces = item.getRawInterfaces().stream()
                .filter(types);
            List<String> implementedClasses = Stream.concat(matchingSuperclass.map(Stream::of).orElse(Stream.empty()), matchingInterfaces)
                .map(JavaClass::getName)
                .collect(Collectors.toList());
            boolean fulfilled = !implementedClasses.isEmpty();
            String verb = implementedClasses.size() == 1 ? "is" : "are";
            String classesDescription = fulfilled ? String.join(", ", implementedClasses) : "no classes";
            String message = String.format("%s extends/implements %s that %s %s in %s",
                item.getDescription(),
                classesDescription,
                verb,
                types.getDescription(),
                item.getSourceCodeLocation()
            );
            events.add(new SimpleConditionEvent(item, fulfilled, message));
        }
    }

    class AnnotatedMaybeInSupertypePredicate extends DescribedPredicate<JavaMember> {
        private final DescribedPredicate<? super JavaAnnotation<?>> predicate;

        AnnotatedMaybeInSupertypePredicate(DescribedPredicate<? super JavaAnnotation<?>> predicate) {
            super("annotated, maybe in a supertype, with " + predicate.getDescription());
            this.predicate = predicate;
        }

        @Override
        public boolean test(JavaMember input) {
            Stream<JavaClass> ownerAndSupertypes = Stream.of(
                Stream.of(input.getOwner()),
                input.getOwner().getAllRawSuperclasses().stream(),
                input.getOwner().getAllRawInterfaces().stream()
            ).flatMap(Function.identity());

            return ownerAndSupertypes.anyMatch(classInHierarchy ->
                findMatchingCallableMember(classInHierarchy, input)
                    .map(member -> member.isAnnotatedWith(predicate))
                    .orElse(false)
            );
        }

        private Optional<? extends JavaMember> findMatchingCallableMember(JavaClass owner, JavaMember memberToFind) {
            if (owner.equals(memberToFind.getOwner())) {
                return Optional.of(memberToFind);
            }

            // only methods can be overridden, while constructors and fields are always referenced directly
            if (memberToFind instanceof JavaMethod) {
                String[] parameterFqNames = ((JavaMethod) memberToFind).getParameters().stream()
                    .map(it -> it.getRawType().getFullName())
                    .toArray(String[]::new);
                return owner.tryGetMethod(memberToFind.getName(), parameterFqNames);
            } else {
                return Optional.empty();
            }
        }
    }

    class AnnotatedOrInPackageAnnotatedPredicate extends DescribedPredicate<JavaClass> {
        private final Class<? extends Annotation> annotationType;

        AnnotatedOrInPackageAnnotatedPredicate(Class<? extends Annotation> annotationType) {
            super("annotated (directly or via its package) with @" + annotationType.getName());
            this.annotationType = annotationType;
        }

        @Override
        public boolean test(JavaClass input) {
            return input.isAnnotatedWith(annotationType) || input.getPackage().isAnnotatedWith(annotationType);
        }
    }
}
