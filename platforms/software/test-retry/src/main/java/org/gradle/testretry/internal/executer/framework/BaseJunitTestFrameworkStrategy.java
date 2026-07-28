/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.testretry.internal.executer.TestFilterBuilder;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;
import org.gradle.testretry.internal.testsreader.TestsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

abstract class BaseJunitTestFrameworkStrategy implements TestFrameworkStrategy {

    public static final Logger LOGGER = LoggerFactory.getLogger(JunitTestFrameworkStrategy.class);
    private static final Pattern PARAMETERIZED_SUFFIX_PATTERN = Pattern.compile("(?:\\([^)]*?\\))?(?:\\[[^]]*?])*$");
    private static final String ERROR_SYNTHETIC_TESTNG_CLASS_NAME = "UnknownClass";
    static final Set<String> ERROR_SYNTHETIC_TEST_NAMES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "classMethod",
            "executionError",
            "initializationError",
            "unnecessary Mockito stubbings"
        ))
    );

    @Override
    public boolean isLifecycleFailureTest(TestsReader testsReader, String className, String testName) {
        return ERROR_SYNTHETIC_TEST_NAMES.contains(testName);
    }

    @Override
    public boolean isExpectedUnretriedTest(String className, String test) {
        return ERROR_SYNTHETIC_TESTNG_CLASS_NAME.equals(className) || className.isEmpty();
    }

    protected DefaultTestFilter testFilterFor(TestNames failedTests, boolean canRunParameterizedSpockMethods, TestFrameworkTemplate template, Set<String> testClassesSeenInCurrentRound) {
        TestFilterBuilder filter = template.filterBuilder();
        addFilters(filter, template.testsReader, failedTests, canRunParameterizedSpockMethods, testClassesSeenInCurrentRound);
        return filter.build();
    }

    protected void addFilters(TestFilterBuilder filters, TestsReader testsReader, TestNames failedTests, boolean canRunParameterizedSpockMethods, Set<String> testClassesSeenInCurrentRound) {
        failedTests.stream()
            .forEach(entry -> {
                String className = entry.getKey();
                Set<String> tests = entry.getValue();

                if (tests.isEmpty()) {
                    filters.clazz(className);
                    return;
                }

                if (tests.stream().anyMatch(ERROR_SYNTHETIC_TEST_NAMES::contains)) {
                    if (ERROR_SYNTHETIC_TESTNG_CLASS_NAME.equals(className) || className.isEmpty()) {
                        // Gradle can't properly attribute some of the TestNG lifecycle method failures to classes
                        // Those are @BeforeTest, @AfterTest, @AfterClass methods
                        // In case of @BeforeTest and @AfterTest, we should retry all classes belonging to the TestNG Test
                        // However, we don't know this association and retry all classes instead
                        // In case of @AfterClass, we should be able to retry just a single class
                        // Due to erroneous reporting from TestNG we don't know which one it is
                        testClassesSeenInCurrentRound.forEach(filters::clazz);
                    } else {
                        filters.clazz(className);
                    }
                    return;
                }

                if (processSpockTest(filters, testsReader, canRunParameterizedSpockMethods, className, tests)) {
                    return;
                }

                if (processTestNGTest(filters, testsReader, className, tests)) {
                    return;
                }

                tests.forEach(name -> addPotentiallyParameterizedSuffixed(filters, className, name));
            });
    }

    private boolean processSpockTest(TestFilterBuilder filters, TestsReader testsReader, boolean canRunParameterizedSpockMethods, String className, Set<String> tests) {
        try {
            Optional<Map<String, List<String>>> resultOpt = testsReader.readTestClassDirClass(className, () -> new SpockParameterClassVisitor(tests, testsReader));
            if (resultOpt.isPresent()) {
                Map<String, List<String>> result = resultOpt.get();
                if (result.isEmpty()) {
                    return false; // not a spec
                }

                if (canRunParameterizedSpockMethods) {
                    result.forEach((test, matches) -> {
                        if (matches.isEmpty()) {
                            addPotentiallyParameterizedSuffixed(filters, className, test);
                        } else {
                            matches.forEach(match -> filters.test(className, match));
                        }
                    });
                } else {
                    boolean allLiteralMethodMatches = result.entrySet()
                        .stream()
                        .allMatch(e2 -> e2.getValue().size() == 1 && e2.getValue().get(0).equals(e2.getKey()));

                    if (allLiteralMethodMatches) {
                        tests.forEach(test -> filters.test(className, test));
                    } else {
                        filters.clazz(className);
                    }
                }

                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine if class {} contains Spock @Unroll parameterizations", className, t);
        }
        return false;
    }

    private boolean processTestNGTest(TestFilterBuilder filters, TestsReader testsReader, String className, Set<String> tests) {
        try {
            Optional<TestNgClassVisitor.ClassInfo> resultOpt = testsReader.readTestClassDirClass(className, TestNgClassVisitor::new);
            if (resultOpt.isPresent()) {
                TestNgClassVisitor.ClassInfo result = resultOpt.get();

                tests.forEach(test -> {
                    addPotentiallyParameterizedSuffixed(filters, className, test);
                    result.dependsOn(test).forEach(dependency -> filters.test(className, dependency));
                });

                return true;
            }
        } catch (Throwable t) {
            LOGGER.warn("Unable to determine dependency between methods of a TestNG test class {}", className, t);
        }

        return false;
    }

    private void addPotentiallyParameterizedSuffixed(TestFilterBuilder filters, String className, String name) {
        // It's a common pattern to add all the parameters on the end of a literal method name with []
        // The regex takes care of removing trailing (...) or (...)[...], or (...)[...][...] for e.g. the following cases
        // * `test that contains (parentheses)()`
        // * `test that contains (parentheses)(int, int)[1]`
        // * `test(1, true) [0]`
        // * `dynamicContainerTest()[1][1]`
        String strippedParameterName = PARAMETERIZED_SUFFIX_PATTERN.matcher(name).replaceAll("");
        filters.test(className, strippedParameterName);
        filters.test(className, name);
    }
}
