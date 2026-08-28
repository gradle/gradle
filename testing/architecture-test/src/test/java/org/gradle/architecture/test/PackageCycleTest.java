/*
 * Copyright 2026 the original author or authors.
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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.PackageMatchers;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that there are no package cycles between the production classes of each module.
 *
 * <p>
 * This test checks all first-party production classes in a single run, evaluating the cycle
 * rule separately for each module. It replaces the previous per-project {@code archTest} setup,
 * where the same check ran once in every project (~240 times per build), each run paying the
 * full ArchUnit import cost, see
 * <a href="https://github.com/gradle/gradle-private/issues/4682">gradle-private#4682</a>.
 * </p>
 *
 * <p>
 * The per-module evaluation preserves the semantics of the previous setup: only dependencies
 * between classes of the same module are considered, and exclude patterns only apply to the
 * module that declares them. Package cycles that span multiple modules are not detected,
 * just like they were not detected by the per-project setup.
 * </p>
 *
 * <p>
 * A module is identified by the class file location of its classes, which contains the module
 * build directory. Classes not loaded from a module build directory are third-party
 * (e.g. {@code org.gradle.fileevents}) and do not participate in cycle detection.
 * </p>
 *
 * <p>
 * Exclude pattern syntax (inherited from the previous setup): {@code /*} matches a single
 * package, {@code /**} matches a package subtree, a trailing {@code **} without a preceding
 * {@code /} matches classes by fully qualified name prefix.
 * </p>
 */
@AnalyzeClasses(packages = "org.gradle")
public class PackageCycleTest {

    private static final Map<String, List<String>> PER_MODULE_EXCLUDE_PATTERNS = Map.ofEntries(
        entry("antlr", List.of("org/gradle/api/plugins/antlr/internal/*")),
        entry("base-diagnostics", List.of("org/gradle/api/reporting/model/internal/*")),
        // Needed for the factory methods in the base class
        entry("base-services", List.of("org/gradle/util/GradleVersion**")),
        entry("base-ide-plugins", List.of(
            "org/gradle/plugins/ide/idea/internal/**",
            "org/gradle/plugins/ide/idea/model/internal/**"
        )),
        entry("build-init", List.of("org/gradle/api/tasks/wrapper/internal/*")),
        // Pre-existing cycles between classpath subpackages (classpath <-> intercept, classpath <-> transforms)
        entry("classpath", List.of("org/gradle/internal/classpath/**")),
        entry("code-quality", List.of("org/gradle/api/plugins/quality/internal/*")),
        entry("configuration-cache", List.of("org/gradle/internal/cc/**")),
        entry("core", List.of("org/gradle/**")),
        entry("core-api", List.of("org/gradle/**")),
        entry("dependency-management", List.of("org/gradle/**")),
        entry("domain-object-collections", List.of("org/gradle/api/internal/**")),
        entry("ear", List.of("org/gradle/plugins/ear/internal/*")),
        // Some cycles have been inherited from the time these classes were in :core
        entry("file-collections", List.of("org/gradle/api/internal/file/collections/**")),
        entry("file-operations", List.of("org/gradle/api/internal/**")),
        entry("ide", List.of(
            "org/gradle/plugins/ide/internal/*",
            "org/gradle/plugins/ide/eclipse/internal/*",
            "org/gradle/plugins/ide/idea/internal/*",
            "org/gradle/plugins/ide/eclipse/model/internal/*",
            "org/gradle/plugins/ide/idea/model/internal/*"
        )),
        entry("ide-plugins", List.of("org/gradle/plugins/ide/idea/**")),
        entry("jacoco", List.of(
            "org/gradle/internal/jacoco/*",
            "org/gradle/testing/jacoco/plugins/*"
        )),
        entry("java-api-extractor", List.of("org/gradle/internal/tools/api/impl/*")),
        entry("java-platform", List.of("org/gradle/api/internal/java/**")),
        // These public packages have classes that are tangled with the corresponding internal package.
        entry("javadoc", List.of(
            "org/gradle/external/javadoc/**",
            "org/gradle/api/tasks/javadoc/**"
        )),
        // Needed for the factory methods in the interface since the implementation is in an internal package
        // which in turn references the interface.
        entry("jvm-services", List.of(
            "org/gradle/jvm/toolchain/JavaLanguageVersion**",
            "org/gradle/jvm/toolchain/JvmVendorSpec**",
            "org/gradle/jvm/toolchain/internal/DefaultJvmVendorSpec**"
        )),
        entry("kotlin-dsl", List.of("org/gradle/kotlin/dsl/**")),
        entry("kotlin-dsl-plugins", List.of(
            "org/gradle/kotlin/dsl/plugins/base/**",
            "org/gradle/kotlin/dsl/plugins/precompiled/**"
        )),
        entry("kotlin-dsl-provider-plugins", List.of("org/gradle/kotlin/dsl/provider/plugins/precompiled/tasks/**")),
        entry("language-groovy", List.of(
            "org/gradle/api/internal/tasks/compile/**",
            "org/gradle/api/tasks/javadoc/**"
        )),
        // These public packages have classes that are tangled with the corresponding internal package.
        entry("language-java", List.of("org/gradle/api/tasks/**")),
        entry("language-native", List.of("org/gradle/language/nativeplatform/internal/**")),
        entry("logging", List.of(
            "org/gradle/internal/featurelifecycle/**",
            "org/gradle/util/**"
        )),
        entry("maven", List.of(
            "org/gradle/api/publication/maven/internal/**",
            "org/gradle/api/artifacts/maven/**"
        )),
        entry("model-core", List.of(
            "org/gradle/model/internal/core/**",
            "org/gradle/model/internal/inspect/**",
            "org/gradle/api/internal/tasks/**",
            "org/gradle/model/internal/manage/schema/**",
            "org/gradle/model/internal/type/**",
            "org/gradle/api/internal/plugins/*",
            // cycle between org.gradle.api.internal.provider and org.gradle.util.internal
            // (api.internal.provider -> ConfigureUtil, DeferredUtil -> api.internal.provider)
            "org/gradle/util/internal/*"
        )),
        // Cycle between public interface, Factory and implementation class in internal package
        entry("native", List.of("org/gradle/platform/internal/**")),
        entry("platform-base", List.of("org/gradle/**")),
        entry("platform-native", List.of(
            "org/gradle/nativeplatform/plugins/**",
            "org/gradle/nativeplatform/tasks/**",
            "org/gradle/nativeplatform/internal/resolve/**",
            "org/gradle/nativeplatform/toolchain/internal/**"
        )),
        entry("plugins-application", List.of("org/gradle/api/plugins/**")),
        entry("plugins-java", List.of("org/gradle/api/plugins/**")),
        entry("plugins-java-base", List.of("org/gradle/api/plugins/**")),
        entry("plugins-model-native", List.of("org/gradle/**")),
        entry("plugins-version-catalog", List.of("org/gradle/**")),
        // ProblemId.create() and ProblemGroup.create() return internal types
        entry("problems-api", List.of("org/gradle/api/problems/**")),
        entry("process-memory-services", List.of("org/gradle/process/internal/**")),
        entry("process-services", List.of("org/gradle/process/internal/**")),
        entry("reporting", List.of("org/gradle/api/reporting/internal/**")),
        entry("scala", List.of(
            "org/gradle/api/internal/tasks/scala/**",
            "org/gradle/api/tasks/*",
            "org/gradle/api/tasks/scala/internal/*",
            "org/gradle/language/scala/tasks/*"
        )),
        entry("signing", List.of("org/gradle/plugins/signing/**")),
        entry("software-diagnostics", List.of(
            "org/gradle/api/reporting/dependencies/internal/*",
            "org/gradle/api/plugins/internal/*"
        )),
        entry("start-parameter", List.of("org/gradle/**")),
        entry("test-kit", List.of("org/gradle/testkit/runner/internal/**")),
        entry("testing-base", List.of("org/gradle/api/internal/tasks/testing/**")),
        entry("testing-base-infrastructure", List.of("org/gradle/api/internal/tasks/testing/**")),
        entry("testing-jvm", List.of("org/gradle/api/internal/tasks/testing/**")),
        entry("tooling-api", List.of("org/gradle/tooling/**")),
        // Needed for the factory methods in the interface
        entry("toolchains-jvm", List.of("org/gradle/jvm/toolchain/**")),
        entry("toolchains-jvm-shared", List.of("org/gradle/jvm/toolchain/JavaToolchainDownload**")),
        entry("war", List.of("org/gradle/api/plugins/internal/*")),
        entry("worker-process-services", List.of("org/gradle/process/internal/worker/**"))
    );

    private static final Pattern MODULE_FROM_LOCATION = Pattern.compile("/([^/]+)/build/(classes|libs)/");

    private static final Map<String, PackageMatchers> PER_MODULE_IGNORED_PACKAGES = PER_MODULE_EXCLUDE_PATTERNS.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> packageMatchersOf(e.getValue())));
    private static final Map<String, Set<String>> PER_MODULE_IGNORED_CLASS_PREFIXES = PER_MODULE_EXCLUDE_PATTERNS.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> classPrefixesOf(e.getValue())));

    @ArchTest
    public static void there_are_no_package_cycles(JavaClasses classes) {
        List<String> violations = modulesOf(classes).stream()
            .map(module -> resultForModule(classes, module))
            .filter(EvaluationResult::hasViolation)
            .map(result -> result.getFailureReport().toString())
            .collect(Collectors.toList());
        if (!violations.isEmpty()) {
            fail(String.join("\n\n", violations));
        }
    }

    private static Set<String> modulesOf(JavaClasses classes) {
        return classes.stream()
            .map(PackageCycleTest::moduleOf)
            .flatMap(Optional::stream)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static EvaluationResult resultForModule(JavaClasses classes, String module) {
        return SlicesRuleDefinition.slices()
            .assignedFrom(sliceAssignmentForModule(module))
            .should()
            .beFreeOfCycles()
            // Some modules exclude all their classes, that is why we allow empty here
            .allowEmptyShould(true)
            .evaluate(classes);
    }

    /**
     * Assigns only the given module's classes to slices, one slice per package.
     * All other classes are ignored, so just like in the previous per-project setup,
     * only dependencies between classes of the same module can form a detectable cycle.
     */
    private static SliceAssignment sliceAssignmentForModule(String module) {
        return new SliceAssignment() {
            @Override
            public String getDescription() {
                return "slices matching 'org.gradle.(**)' in module '" + module + "'";
            }

            @Override
            public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                boolean inModule = moduleOf(javaClass).map(module::equals).orElse(false);
                if (!inModule || isIgnored(javaClass, module)) {
                    return SliceIdentifier.ignore();
                }
                return SliceIdentifier.of(javaClass.getPackageName());
            }
        };
    }

    // Cached because the module of every class is queried once per module-scoped rule evaluation.
    private static final Map<JavaClass, Optional<String>> MODULE_CACHE = new ConcurrentHashMap<>();

    private static Optional<String> moduleOf(JavaClass javaClass) {
        return MODULE_CACHE.computeIfAbsent(javaClass, clazz -> {
            String location = clazz.getSource().map(source -> source.getUri().toString()).orElse("");
            Matcher matcher = MODULE_FROM_LOCATION.matcher(location);
            return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
        });
    }

    private static boolean isIgnored(JavaClass javaClass, String module) {
        if (javaClass.isAnnotation()) {
            return true;
        }
        PackageMatchers ignoredPackages = PER_MODULE_IGNORED_PACKAGES.get(module);
        if (ignoredPackages == null) {
            return false;
        }
        return ignoredPackages.test(javaClass.getPackageName())
            || PER_MODULE_IGNORED_CLASS_PREFIXES.get(module).stream().anyMatch(prefix -> javaClass.getFullName().startsWith(prefix));
    }

    private static boolean isClassNamePattern(String pattern) {
        return pattern.endsWith("*") && !(pattern.endsWith("/*") || pattern.endsWith("/**"));
    }

    private static PackageMatchers packageMatchersOf(List<String> patterns) {
        return PackageMatchers.of(patterns.stream()
            .filter(pattern -> !isClassNamePattern(pattern))
            .map(pattern -> pattern.replace("/**", ".."))
            .map(pattern -> pattern.replace("/*", ""))
            .map(pattern -> pattern.replace("/", "."))
            .collect(Collectors.toSet()));
    }

    private static Set<String> classPrefixesOf(List<String> patterns) {
        return patterns.stream()
            .filter(PackageCycleTest::isClassNamePattern)
            .map(pattern -> pattern.replace("/", "."))
            .map(pattern -> pattern.replace("*", ""))
            .collect(Collectors.toSet());
    }
}
