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

package org.gradle.internal;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "org.gradle.internal")
public class PackageStructureTest {
    @ArchTest
    public static final ArchRule implementation_cannot_access_factory = noClasses()
        .that().resideInAPackage("..implementation..")
        .should()
        .accessClassesThat().resideInAPackage("..factory..");

    @ArchTest
    public static final ArchRule api_cannot_access_implementation = noClasses()
        .that().resideInAPackage("..api..")
        .should()
        .accessClassesThat().resideInAPackage("..implementation..");

    @ArchTest
    public static final ArchRule api_cannot_access_spi = noClasses()
        .that().resideInAPackage("..api..")
        .should()
        .accessClassesThat().resideInAPackage("..spi..");

    @ArchTest
    public static final ArchRule spi_cannot_access_implementation = noClasses()
        .that().resideInAPackage("..spi..")
        .should()
        .accessClassesThat().resideInAPackage("..implementation..");

    @ArchTest
    public static final ArchRule outside_classes_cannot_access_implementation = noClasses()
        .that().resideOutsideOfPackages("org.gradle.internal.snapshot.implementation..", "org.gradle.internal.snapshot.factory..")
        .should()
        .accessClassesThat().resideInAPackage("org.gradle.internal.snapshot.implementation..");
}
