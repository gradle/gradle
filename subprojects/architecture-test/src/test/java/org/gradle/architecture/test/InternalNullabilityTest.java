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
import org.gradle.api.NonNullApi;

import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.gradle.architecture.test.ArchUnitFixture.beAnnotatedOrInPackageAnnotatedWith;
import static org.gradle.architecture.test.ArchUnitFixture.classes_not_written_in_kotlin;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.inGradleInternalApiPackages;

@AnalyzeClasses(packages = "org.gradle")
public class InternalNullabilityTest {

    /**
     * This test validates that all internal classes are annotated with {@link NonNullApi}.
     * <p>
     * The annotation can be applied on the class directly, but the preferred way is to annotate the package by adding or updating the {@code package-info.java} file.
     * See {@code subprojects/core-api/src/main/java/org/gradle/package-info.java} for an example.
     * <p>
     * Note that adding the annotation for a package in one subproject will automatically apply it for the same package in all other subprojects.
     * Therefore, it's advised to add the annotation to the package in the most appropriate subproject.
     * For instance, if the package exists in both {@code :core} and {@code :base-services}, it should be annotated in {@code :base-services}.
     */
    @ArchTest
    public static final ArchRule internal_classes_are_annotated_with_non_null_api = freeze(classes()
        .that(are(inGradleInternalApiPackages())).and(classes_not_written_in_kotlin)
        .should(beAnnotatedOrInPackageAnnotatedWith(NonNullApi.class)));

}
