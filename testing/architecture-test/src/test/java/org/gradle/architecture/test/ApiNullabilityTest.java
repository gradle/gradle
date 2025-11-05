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

package org.gradle.architecture.test;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.gradle.architecture.test.ArchUnitFixture.beNullMarkedClass;
import static org.gradle.architecture.test.ArchUnitFixture.beNullUnmarkedMethod;
import static org.gradle.architecture.test.ArchUnitFixture.classes_not_written_in_kotlin;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.inGradleInternalApiPackages;
import static org.gradle.architecture.test.ArchUnitFixture.inGradlePublicApiPackages;
import static org.gradle.architecture.test.ArchUnitFixture.not_anonymous_classes;
import static org.gradle.architecture.test.ArchUnitFixture.not_synthetic_classes;
import static org.gradle.architecture.test.ArchUnitFixture.public_api_methods;

/**
 * This test validates that classes are annotated with {@link NullMarked} and not with {@link NullUnmarked}.
 * <p>
 * The {@link NullMarked} annotation can be applied on the class directly,
 * but the preferred way is to annotate the package by adding or updating the {@code package-info.java} file.
 * See {@code subprojects/core-api/src/main/java/org/gradle/package-info.java} for an example.
 * <p>
 * Note that when adding the {@link NullMarked} annotation on a package that is split across multiple subprojects,
 * then you must add it to each of the split of the package.
 * For instance, if the package exists in both {@code :core} and {@code :base-services}, it should be annotated in both.
 */
@AnalyzeClasses(packages = "org.gradle")
public class ApiNullabilityTest {

    @ArchTest
    public static final ArchRule internal_classes_are_annotated_with_non_null_api = freeze(classes()
        .that(are(inGradleInternalApiPackages()))
        .and(classes_not_written_in_kotlin)
        .and(not_synthetic_classes)
        .and(not_anonymous_classes)
        .should(beNullMarkedClass()));

    @ArchTest
    public static final ArchRule public_api_classes_are_annotated_with_non_null_api = freeze(classes()
        .that(are(inGradlePublicApiPackages()))
        .and(classes_not_written_in_kotlin)
        .and(not_synthetic_classes)
        .and(not_anonymous_classes)
        .should(beNullMarkedClass()));

    @ArchTest
    public static final ArchRule public_api_methods_are_not_null_unmarked = freeze(methods()
        .that(are(public_api_methods))
        .should(not(beNullUnmarkedMethod())));
}
