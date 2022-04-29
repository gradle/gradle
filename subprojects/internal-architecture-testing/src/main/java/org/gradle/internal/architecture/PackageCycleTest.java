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

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.PackageMatchers;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@AnalyzeClasses(
    packages = "org.gradle",
    importOptions = ImportOption.DoNotIncludeJars.class
)
public class PackageCycleTest {

    private static final DescribedPredicate<JavaClass> in_ignored_packages = new DescribedPredicate<JavaClass>("in ignored packages") {
        private final PackageMatchers excludedPackages = PackageMatchers.of(ignoredPackagesForCycles());

        @Override
        public boolean apply(JavaClass javaClass) {
            return excludedPackages.apply(javaClass.getPackageName());
        }
    };

    private static final DescribedPredicate<JavaClass> ignored_class = new DescribedPredicate<JavaClass>("ignored class") {
        private final Set<String> ignoredClassPrefixes = ignoredClassesForCycles();

        @Override
        public boolean apply(JavaClass javaClass) {
            return javaClass.isAnnotation() || ignoredClassPrefixes.stream().anyMatch(prefix -> javaClass.getFullName().startsWith(prefix));
        }
    };

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

    private static final SliceAssignment packagesWithoutIgnored = new SliceAssignment() {
        @Override
        public String getDescription() {
            return "slices matching 'org.gradle.(**)";
        }

        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            if (in_ignored_packages.apply(javaClass) || ignored_class.apply(javaClass)) {
                return SliceIdentifier.ignore();
            }
            return SliceIdentifier.of(javaClass.getPackageName());
        }
    };

    @ArchTest
    public static final ArchRule there_are_no_package_cycles =
        SlicesRuleDefinition.slices().assignedFrom(packagesWithoutIgnored)
            .should()
            .beFreeOfCycles();
}
