/*
 * Copyright 2024 the original author or authors.
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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "org.gradle")
public class NoKotlinStdlibBinaryDependencyTest {
    @ArchTest
    public static final ArchRule schema_types_do_not_have_binary_dependencies_on_kotlin_stdlib =
        // The `declarative-dsl-tooling-models` should be usable in clients that have no Kotlin stdlib.
        // We check that it has no references to the stdlib types, except for the @kotlin.Metadata annotation.
        classes().that()
            .resideInAPackage("org.gradle.declarative.dsl.schema").or()
            .resideInAPackage("org.gradle.declarative.dsl.evaluation")
            .should().onlyDependOnClassesThat().haveNameNotMatching("kotlin\\.(?!Metadata$).*");
}
