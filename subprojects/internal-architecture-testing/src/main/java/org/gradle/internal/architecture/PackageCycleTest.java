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
import com.tngtech.archunit.core.domain.PackageMatchers;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.thirdparty.com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@AnalyzeClasses(
    packages = "org.gradle",
    importOptions = ImportOption.DoNotIncludeJars.class
)
public class PackageCycleTest {
    private static final PackageMatchers IGNORED_PACKAGES_FOR_CYCLES = PackageMatchers.of(ignoredPackagesForCycles());
    private static final ImmutableSet<String> IGNORED_CLASSES_FOR_CYCLES = ImmutableSet.copyOf(ignoredClassesForCycles());

    private static boolean isInIgnoredPackage(JavaClass javaClass) {
        return IGNORED_PACKAGES_FOR_CYCLES.test(javaClass.getPackageName());
    }

    private static boolean isIgnoredClass(JavaClass javaClass) {
        return javaClass.isAnnotation() || IGNORED_CLASSES_FOR_CYCLES.stream().anyMatch(prefix -> javaClass.getFullName().startsWith(prefix));
    }

    private static Set<String> ignoredPackagesForCycles() {
        String patterns = System.getProperty("package.cycle.exclude.patterns");
        return Arrays.stream(patterns.split(","))
            .map(String::trim)
            .filter(pattern -> !isClassNamePattern(pattern))
            .map(pattern -> pattern.replace("/**", ".."))
            .map(pattern -> pattern.replace("/*", ""))
            .map(pattern -> pattern.replace("/", "."))
            .collect(Collectors.toSet());
    }

    private static boolean isClassNamePattern(String pattern) {
        return pattern.endsWith("*") && !(pattern.endsWith("/*") || pattern.endsWith("/**"));
    }

    private static Set<String> ignoredClassesForCycles() {
        String patterns = System.getProperty("package.cycle.exclude.patterns");
        return Arrays.stream(patterns.split(" "))
            .map(String::trim)
            .filter(PackageCycleTest::isClassNamePattern)
            .map(pattern -> pattern.replace("/", "."))
            .map(pattern -> pattern.replace("*", ""))
            .collect(Collectors.toSet());
    }

    private static final SliceAssignment GRADLE_SLICE_ASSIGNMENT = new SliceAssignment() {
        @Override
        public String getDescription() {
            return "slices matching 'org.gradle.(**)";
        }

        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            if (isInIgnoredPackage(javaClass) || isIgnoredClass(javaClass)) {
                return SliceIdentifier.ignore();
            }
            return SliceIdentifier.of(javaClass.getPackageName());
        }
    };

    @ArchTest
    public static final ArchRule there_are_no_package_cycles =
        SlicesRuleDefinition.slices().assignedFrom(GRADLE_SLICE_ASSIGNMENT)
            .should()
            .beFreeOfCycles()
            // Some projects exclude all classes, that is why we allow empty here
            .allowEmptyShould(true);
}
