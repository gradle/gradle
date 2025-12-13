/*
 * Copyright 2025 the original author or authors.
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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static java.util.stream.Collectors.toSet;
import static org.gradle.architecture.test.ArchUnitFixture.classes_not_written_in_kotlin;
import static org.gradle.architecture.test.ArchUnitFixture.getClassFile;
import static org.gradle.architecture.test.ArchUnitFixture.not_from_fileevents_classes;
import static org.gradle.architecture.test.ArchUnitFixture.not_synthetic_classes;

@AnalyzeClasses(packages = "org.gradle")
public class ForbiddenNullabilityAnnotationsTest {

    @ArchTest
    public static final ArchRule jsr305_nullability_annotations_are_not_used = classes()
        .that(arePublicAndInternalJavaClasses())
        .should(notBeAnnotatedWith(jsr305NullabilityAnnotations()));

    @ArchTest
    public static final ArchRule jetbrains_nullability_annotations_are_not_used = classes()
        .that(arePublicAndInternalJavaClasses())
        .should(notBeAnnotatedWith(jetbrainsNullabilityAnnotations()));

    static DescribedPredicate<JavaClass> arePublicAndInternalJavaClasses() {
        return resideInAnyPackage("org.gradle..")
            .and(not(oldNonnullAnnotations()))
            .and(not(isInternalTestingCode()))
            .and(classes_not_written_in_kotlin)
            .and(not_from_fileevents_classes)
            .and(not_synthetic_classes)
            .as("public and internal Java production classes");
    }

    /**
     * The ArchUnit classpath includes some internal testing code that we need to ignore in this test.
     */
    static DescribedPredicate<JavaClass> isInternalTestingCode() {
        return new DescribedPredicate<JavaClass>("internal testing code") {
            @Override
            public boolean test(JavaClass javaClass) {
                Path classFilePath = getClassFile(javaClass);
                if (classFilePath == null) {
                    return false;
                }
                return StreamSupport.stream(classFilePath.spliterator(), false)
                    .anyMatch(p -> p.toString().startsWith("internal-") && p.toString().endsWith("-testing"));
            }
        };
    }

    static DescribedPredicate<JavaClass> oldNonnullAnnotations() {
        return new DescribedPredicate<JavaClass>("old meta annotations") {
            @Override
            public boolean test(JavaClass javaClass) {
                return Arrays.asList(
                    "org.gradle.api.NonNullApi",
                    "org.gradle.util.GroovyNullMarked"
                ).contains(javaClass.getName());
            }
        };
    }

    static DescribedPredicate<JavaAnnotation<?>> jsr305NullabilityAnnotations() {
        return new DescribedPredicate<JavaAnnotation<?>>("JSR-305 nullability annotations") {
            @Override
            public boolean test(JavaAnnotation<?> javaAnnotation) {
                return Arrays.asList(
                    "javax.annotation.Nullable",
                    "javax.annotation.Nonnull",
                    "javax.annotation.CheckForNull",
                    "javax.annotation.ParametersAreNonnullByDefault",
                    "javax.annotation.ParametersAreNullableByDefault"
                ).contains(javaAnnotation.getRawType().getName());
            }
        };
    }

    static DescribedPredicate<JavaAnnotation<?>> jetbrainsNullabilityAnnotations() {
        return new DescribedPredicate<JavaAnnotation<?>>("JetBrains nullability annotations") {
            @Override
            public boolean test(JavaAnnotation<?> javaAnnotation) {
                return Arrays.asList(
                    "org.jetbrains.annotations.Nullable",
                    "org.jetbrains.annotations.NotNull",
                    "org.jetbrains.annotations.UnknownNullability"
                ).contains(javaAnnotation.getRawType().getName());
            }
        };
    }

    static ArchCondition<JavaClass> notBeAnnotatedWith(DescribedPredicate<JavaAnnotation<?>> annotations) {
        return new ArchCondition<JavaClass>("not be annotated with " + annotations.getDescription()) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (allAnnotationsOf(javaClass).stream().anyMatch(annotations)) {
                    events.add(new SimpleConditionEvent(javaClass, false, javaClass.getName() + " is annotated with " + annotations.getDescription()));
                }
            }

            private Set<JavaAnnotation<?>> allAnnotationsOf(JavaClass javaClass) {
                Set<JavaAnnotation<?>> allAnnotations = new LinkedHashSet<>();
                allAnnotations.addAll(javaClass.getAnnotations());
                allAnnotations.addAll(javaClass.getFields().stream().flatMap(f -> f.getAnnotations().stream()).collect(toSet()));
                allAnnotations.addAll(javaClass.getConstructors().stream().flatMap(c -> c.getParameterAnnotations().stream().flatMap(Collection::stream)).collect(toSet()));
                allAnnotations.addAll(javaClass.getMethods().stream().flatMap(m -> Stream.concat(
                    m.getParameters().stream().flatMap(p -> p.getAnnotations().stream()),
                    m.getAnnotations().stream()
                )).collect(toSet()));
                return allAnnotations;
            }
        };
    }
}
