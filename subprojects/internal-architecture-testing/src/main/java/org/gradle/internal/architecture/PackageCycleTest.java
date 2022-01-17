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

package org.gradle.internal.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.thirdparty.com.google.common.collect.ImmutableSet;

import java.util.Set;

@AnalyzeClasses(packages = "org.gradle")
public class PackageCycleTest {
    private static final Set<String> IGNORED_PACKAGES_FOR_CYCLES = ImmutableSet.of(
        "org.gradle",
        "org.gradle.api",
        "org.gradle.api.internal",
        "org.gradle.util",
        "org.gradle.util.internal",
        "org.gradle.internal.deprecation"
    );

    private static final SliceAssignment packagesWithoutApi = new SliceAssignment() {
        @Override
        public String getDescription() {
            return "slices matching 'org.gradle.(**)";
        }

        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            if (javaClass.isAnnotation() || IGNORED_PACKAGES_FOR_CYCLES.contains(javaClass.getPackageName())) {
                return SliceIdentifier.ignore();
            }
            return SliceIdentifier.of(javaClass.getPackageName());
        }
    };

    @ArchTest
    public static final ArchRule there_are_no_package_cycles =
        SlicesRuleDefinition.slices().assignedFrom(packagesWithoutApi)
            .should()
            .beFreeOfCycles();
}
