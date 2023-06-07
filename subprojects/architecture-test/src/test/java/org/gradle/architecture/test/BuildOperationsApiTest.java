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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.gradle.architecture.test.ArchUnitFixture.beAnnotatedOrInPackageAnnotatedWith;

@AnalyzeClasses(packages = "org.gradle.operations")
public class BuildOperationsApiTest {

    @ArchTest
    public static final ArchRule classes_in_operations_package_are_annotated_with_non_null_api =
        classes().should(beAnnotatedOrInPackageAnnotatedWith(NonNullApi.class));
}
