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
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jspecify.annotations.NullMarked;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.conditions.ArchConditions.not;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.gradle.architecture.test.ArchUnitFixture.freeze;
import static org.gradle.architecture.test.ArchUnitFixture.not_anonymous_classes;
import static org.gradle.architecture.test.ArchUnitFixture.not_synthetic_classes;

/**
 * This test prevents new classes from being placed in overly generic packages.
 * <p>
 * New classes should be placed in more specific, semantically meaningful packages.
 */
@AnalyzeClasses(packages = "org.gradle")
@NullMarked
public class ClassesInGenericPackagesTest {

    @ArchTest
    public static final ArchRule classes_should_not_be_in_generic_packages = freeze(
        classes()
            .that(are(not_synthetic_classes))
            .and(are(not_anonymous_classes))
            .and().areNotMemberClasses()
            .and().doNotHaveSimpleName("package-info")
            .should(not(be(inGenericPackage())))
            .as("no classes should be in a generic package"));

    private static DescribedPredicate<JavaClass> inGenericPackage() {
        return resideInAnyPackage(
            "org.gradle",
            "org.gradle.api",
            "org.gradle.api.internal",
            "org.gradle.internal",

            "org.gradle.api.initialization",
            "org.gradle.initialization",
            "org.gradle.initialization.internal",
            "org.gradle.internal.initialization"
        ).as("in a generic package");
    }
}
