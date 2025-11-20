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
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.test.precondition.Requires;
import org.gradle.test.precondition.TestPrecondition;
import org.gradle.util.EmptyStatement;
import org.gradle.util.Matchers;
import org.gradle.util.SetSystemProperties;
import org.gradle.util.TestClassLoader;
import org.gradle.util.UsesNativeServices;
import org.gradle.util.UsesNativeServicesExtension;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
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
import static com.tngtech.archunit.lang.conditions.ArchConditions.beAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static java.util.stream.Collectors.toSet;

@NullMarked
public interface ArchUnitFixture {
    DescribedPredicate<JavaClass> classes_not_written_in_kotlin =
        not(annotatedOrInPackageAnnotatedWith(kotlin.Metadata.class))
            .and(resideOutsideOfPackages("org.gradle.kotlin..")) // a few relocated kotlinx-metadata classes violate the nullability annotation rules
            .as("classes written in Java or Groovy");

    DescribedPredicate<JavaClass> not_synthetic_classes = new DescribedPredicate<>("not synthetic classes") {
        @Override
        public boolean test(JavaClass javaClass) {
            return !javaClass.getModifiers().contains(JavaModifier.SYNTHETIC);
        }
    };

    DescribedPredicate<JavaClass> not_anonymous_classes = new DescribedPredicate<>("not anonymous classes") {
        @Override
        public boolean test(JavaClass javaClass) {
            return !javaClass.isAnonymousClass();
        }
    };

    DescribedPredicate<JavaMember> not_written_in_kotlin = declaredIn(classes_not_written_in_kotlin)
        .as("written in Java or Groovy");

    DescribedPredicate<JavaMember> not_from_fileevents = declaredIn(resideOutsideOfPackages("org.gradle.fileevents.."))
        .as("not from fileevents");

    DescribedPredicate<JavaClass> not_from_fileevents_classes = resideOutsideOfPackages("org.gradle.fileevents..")
        .as("not from fileevents");

    DescribedPredicate<JavaMember> kotlin_internal_methods = declaredIn(gradlePublicApi())
        .and(not(not_written_in_kotlin))
        .and(modifier(PUBLIC))
        .and(nameMatching(".+\\$[a-z_]+")) // Kotlin internal methods have `$kotlin_module_name` appended to their name
        .as("Kotlin internal methods");

    DescribedPredicate<JavaMember> public_api_methods = declaredIn(gradlePublicApi())
        .and(modifier(PUBLIC))
        .and(not(kotlin_internal_methods))
        .as("public API methods");

    DescribedPredicate<JavaMethod> getters = new DescribedPredicate<JavaMethod>("getters") {
        @Override
        public boolean test(JavaMethod input) {
            PropertyAccessorType accessorType = PropertyAccessorType.fromName(input.getName());
            if (accessorType == PropertyAccessorType.IS_GETTER) {
                // PropertyAccessorType.IS_GETTER doesn't handle names that start with is
                // but are not getters, e.g. issueManagement is detected as IS_GETTER
                return !Character.isLowerCase(input.getName().charAt(2));
            }
            return accessorType == PropertyAccessorType.GET_GETTER;
        }
    };

    static ArchRule freeze(ArchRule rule) {
        return new FreezeInstructionsPrintingArchRule(FreezingArchRule.freeze(rule));
    }

    static DescribedPredicate<JavaClass> gradlePublicApi() {
        return new GradlePublicApi();
    }

    static DescribedPredicate<JavaClass> gradleMaintainedExternalDependency() {
        return resideInAnyPackage(
            "net.rubygrapefruit..",
            "org.gradle.fileevents..")
            .as("Gradle-maintained external dependency");
    }

    static DescribedPredicate<JavaClass> gradleInternalApi() {
        return resideInAnyPackage("org.gradle..")
            .and(not(gradlePublicApi()))
            .and(not(gradleMaintainedExternalDependency()))
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
            .and(not(gradleMaintainedExternalDependency()))
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

    static ArchCondition<JavaClass> beAbstractClass() {
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

    static ArchCondition<JavaMethod> beAbstractMethod() {
        return new ArchCondition<JavaMethod>("be abstract") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.getModifiers().contains(JavaModifier.ABSTRACT)) {
                    events.add(new SimpleConditionEvent(method, true, method.getDescription() + " is abstract"));
                } else {
                    events.add(new SimpleConditionEvent(method, false, method.getDescription() + " is not abstract"));
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

    static ArchCondition<JavaMethod> useJSpecifyNullable() {
        return new ArchCondition<JavaMethod>("use org.jspecify.annotations.Nullable") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Method reflected = safeReflect(method);

                // Check if method return type is annotated with the wrong Nullable
                if (
                    !method.isAnnotatedWith(Nullable.class) &&
                        (reflected == null || reflected.getAnnotatedReturnType().getAnnotation(Nullable.class) == null)
                ) {
                    Set<JavaAnnotation<JavaMethod>> annotations = method.getAnnotations();
                    Set<JavaAnnotation<JavaMethod>> nullableAnnotations = extractPossibleNullable(annotations);
                    if (!nullableAnnotations.isEmpty()) {
                        events.add(new SimpleConditionEvent(method, false, method.getFullName() + " is using forbidden Nullable annotations: " + nullableAnnotations.stream().map(a -> a.getType().getName()).collect(Collectors.joining(","))));
                    } else {
                        events.add(new SimpleConditionEvent(method, true, method.getFullName() + " is not using forbidden Nullable"));
                    }
                }

                // Check if the method's parameters are annotated with the wrong Nullable
                for (int idx = 0; idx < method.getParameters().size(); idx++) {
                    JavaParameter parameter = method.getParameters().get(idx);
                    if (
                        !parameter.isAnnotatedWith(Nullable.class) &&
                            (reflected == null || reflected.getAnnotatedParameterTypes()[idx].getAnnotation(Nullable.class) == null)
                    ) {
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

    static ArchCondition<JavaClass> beNullMarkedClass() {
        return beAnnotatedOrInPackageAnnotatedWith(NullMarked.class).and(not(beAnnotatedWith(NullUnmarked.class)));
    }

    static ArchCondition<JavaMethod> beNullUnmarkedMethod() {
        return beAnnotatedWith(NullUnmarked.class);
    }

    /**
     * Either the class is directly annotated with the given annotation type or the class is in a package that is annotated with the given annotation type.
     */
    static DescribedPredicate<JavaClass> annotatedOrInPackageAnnotatedWith(Class<? extends Annotation> annotationType) {
        return new AnnotatedOrInPackageAnnotatedPredicate(annotationType);
    }

    class HaveGradleTypeEquivalent extends ArchCondition<JavaMethod> {
        public HaveGradleTypeEquivalent() {
            super("have Gradle equivalent to Closure taking method");
        }

        @Override
        public void check(JavaMethod method, ConditionEvents events) {
            if (method.isAnnotatedWith(Deprecated.class)) {
                // Skip deprecated methods
                events.add(new SimpleConditionEvent(method, true, method.getDescription() + " is deprecated, skipping"));
                return;
            }
            List<JavaParameter> parameters = method.getParameters();
            if (!parameters.isEmpty()) {
                JavaParameter lastParameter = parameters.get(parameters.size() - 1);
                // Closure taking method
                if (lastParameter.getRawType().isEquivalentTo(Closure.class)) {
                    // No other methods with the same name and parameters take a Gradle type instead of a Closure
                    List<JavaMethod> similarMethods = findSimilarMethods(method);
                    if (similarMethods.stream().noneMatch(m -> {
                        List<JavaParameter> similarParameters = m.getParameters();
                        JavaParameter last = similarParameters.get(similarParameters.size() - 1);
                        return last.getRawType().isEquivalentTo(Action.class) || last.getRawType().isEquivalentTo(Spec.class) || last.getRawType().isEquivalentTo(Transformer.class);
                    })) {
                        // missing
                        String message = String.format("%s has Closure but does not have equivalent Gradle type method in %s",
                            method.getDescription(),
                            method.getSourceCodeLocation());
                        events.add(new SimpleConditionEvent(method, false, message));
                    }
                }
            }
            events.add(new SimpleConditionEvent(method, true, ""));
        }

        private static List<JavaMethod> findSimilarMethods(JavaMethod method) {
            // This is taking a shortcut and assuming that we do not have multiple methods with the same name and number of parameters taking Closure
            // with _different_ parameter types other than the Closure.
            return method.getOwner().getAllMethods().stream()
                .filter(m -> m != method
                        && m.getName().equals(method.getName())
                        && m.getParameters().size() == method.getParameters().size())
                .collect(Collectors.toList());
        }
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

        public static boolean test(String packageName) {
            return INCLUDES.test(packageName) && !EXCLUDES.test(packageName);
        }

        public InGradlePublicApiPackages() {
            super("in Gradle public API packages");
        }

        @Override
        public boolean test(JavaClass input) {
            String packageName = input.getPackageName();
            return test(packageName);
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
            try {
                // Try to use reflection in order to find `TYPE_USE` annotations
                // See https://github.com/TNG/ArchUnit/issues/1382
                Class<?> clazz = input.reflect();
                return clazz.getAnnotation(annotationType) != null || clazz.getPackage().getAnnotation(annotationType) != null;
            } catch (NoClassDefFoundError e) {
                // Fall back to ArchUnit query
                return input.isAnnotatedWith(annotationType) || input.getPackage().isAnnotatedWith(annotationType);
            }
        }
    }

    @Nullable
    static Method safeReflect(JavaMethod method) {
        try {
            return method.reflect();
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    @Nullable
    static Class<?> safeReflect(JavaClass javaClass) {
        try {
            return javaClass.reflect();
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    @Nullable
    static Path getClassFile(JavaClass javaClass) {
        Class<?> reflectedClass = safeReflect(javaClass);
        if (reflectedClass == null) {
            return null;
        }
        CodeSource codeSource = reflectedClass.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }
        return Paths.get(codeSource.getLocation().getPath());
    }
}
