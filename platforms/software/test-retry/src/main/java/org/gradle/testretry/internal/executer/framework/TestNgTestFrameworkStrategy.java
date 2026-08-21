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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.testretry.internal.executer.TestFilterBuilder;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;
import org.gradle.testretry.internal.executer.framework.TestFrameworkProvider.ProviderForCurrentGradleVersion;
import org.gradle.testretry.internal.testsreader.TestsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy.gradleVersionIsAtLeast;
import static org.gradle.testretry.internal.executer.framework.TestNgTestFrameworkStrategy.TestNGTestFrameworkProvider.testFrameworkProvider;

final class TestNgTestFrameworkStrategy implements TestFrameworkStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestNgTestFrameworkStrategy.class);

    private final Map<String, Optional<TestNgClassVisitor.ClassInfo>> classInfoCache = new HashMap<>();

    @Override
    public boolean isLifecycleFailureTest(TestsReader testsReader, String className, String testName) {
        return getClassInfo(testsReader, className)
            .map(classInfo -> isLifecycleMethod(testsReader, testName, classInfo))
            .orElse(false);
    }

    private boolean isLifecycleMethod(TestsReader testsReader, String testName, TestNgClassVisitor.ClassInfo classInfo) {
        if (classInfo.getLifecycleMethods().contains(testName)) {
            return true;
        } else {
            String superClass = classInfo.getSuperClass();
            if (superClass == null || superClass.equals("java.lang.Object")) {
                return false;
            } else {
                return isLifecycleFailureTest(testsReader, superClass, testName);
            }
        }
    }

    @Override
    public TestFramework createRetrying(TestFrameworkTemplate template, TestFramework testFramework, TestNames failedTests, Set<String> testClassesSeenInCurrentRound) {
        DefaultTestFilter failedTestsFilter = testFilterFor(failedTests, testClassesSeenInCurrentRound, template);

        return testFrameworkProvider(template, testFramework)
            .testFrameworkFor(failedTestsFilter);
    }

    private DefaultTestFilter testFilterFor(TestNames failedTests, Set<String> testClassesSeenInCurrentRound, TestFrameworkTemplate template) {
        TestFilterBuilder filter = template.filterBuilder();
        addFilters(template.testsReader, failedTests,  testClassesSeenInCurrentRound, filter);

        return filter.build();
    }

    private void addFilters(TestsReader testsReader, TestNames failedTests, Set<String> testClassesSeenInCurrentRound, TestFilterBuilder filters) {
        failedTests.stream().forEach(entry -> {
            String className = entry.getKey();
            if (className == null) {
                testClassesSeenInCurrentRound.forEach(filters::clazz);
                return;
            }
            Set<String> tests = entry.getValue();
            if (tests.isEmpty()) {
                filters.clazz(className);
                return;
            }
            Optional<TestNgClassVisitor.ClassInfo> classInfoOpt = getClassInfo(testsReader, className);
            tests.forEach(test -> {
                if (classInfoOpt.isPresent()) {
                    TestNgClassVisitor.ClassInfo classInfo = classInfoOpt.get();
                    if (isLifecycleMethod(testsReader, test, classInfo)) {
                        filters.clazz(className);
                    } else {
                        String parameterlessName = stripParameters(test);
                        filters.test(className, parameterlessName);
                        classInfo.dependsOn(parameterlessName)
                            .forEach(methodName -> filters.test(className, methodName));
                    }
                } else {
                    filters.clazz(className);
                }
            });
        });
    }

    private Optional<TestNgClassVisitor.ClassInfo> getClassInfo(TestsReader testsReader, String className) {
        return classInfoCache.computeIfAbsent(className, ignored -> {
            Optional<TestNgClassVisitor.ClassInfo> classInfoOpt;
            try {
                classInfoOpt = testsReader.readTestClassDirClass(className, TestNgClassVisitor::new);
            } catch (Throwable t) {
                LOGGER.warn("Unable to determine if class " + className + " has TestNG dependent tests", t);
                classInfoOpt = Optional.empty();
            }
            return classInfoOpt;
        });
    }

    private static String stripParameters(String testMethodName) {
        return testMethodName.replaceAll("\\[[^)]+](\\([^)]*\\))+$", "");
    }

    static class TestNGTestFrameworkProvider {
        static class ProviderForGradleOlderThanV8 implements TestFrameworkProvider {

            private final TestFrameworkTemplate template;

            public ProviderForGradleOlderThanV8(TestFrameworkTemplate template) {
                this.template = template;
            }

            @Override
            public TestFramework testFrameworkFor(DefaultTestFilter failedTestsFilter) {
                TestNGTestFramework retryTestFramework = newInstance(template, failedTestsFilter);
                copyOptions((TestNGOptions) template.task.getTestFramework().getOptions(), retryTestFramework.getOptions());

                return retryTestFramework;
            }

            private static TestNGTestFramework newInstance(TestFrameworkTemplate template, DefaultTestFilter failedTestsFilter) {
                try {
                    Class<?> testNGTestFramework = TestNGTestFramework.class;
                    @SuppressWarnings("JavaReflectionMemberAccess") final Constructor<?> constructor = testNGTestFramework.getConstructor(Test.class, FileCollection.class, DefaultTestFilter.class, ObjectFactory.class);

                    return (TestNGTestFramework) constructor.newInstance(template.task, template.task.getClasspath(), failedTestsFilter, template.objectFactory);

                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        static class ProviderForGradleOlderThanV66 implements TestFrameworkProvider {

            private final TestFrameworkTemplate template;

            public ProviderForGradleOlderThanV66(TestFrameworkTemplate template) {
                this.template = template;
            }

            @Override
            public TestFramework testFrameworkFor(DefaultTestFilter failedTestsFilter) {
                TestNGTestFramework retryTestFramework = newInstance(template, failedTestsFilter);
                copyOptions((TestNGOptions) template.task.getTestFramework().getOptions(), retryTestFramework.getOptions());

                return retryTestFramework;
            }

            private TestNGTestFramework newInstance(TestFrameworkTemplate template, DefaultTestFilter failedTestsFilter) {
                try {
                    ServiceRegistry serviceRegistry = ((ProjectInternal) template.task.getProject()).getServices();
                    ClassLoaderCache classLoaderCache = serviceRegistry.get(ClassLoaderCache.class);
                    Class<?> testNGTestFramework = TestNGTestFramework.class;
                    @SuppressWarnings("JavaReflectionMemberAccess") final Constructor<?> constructor = testNGTestFramework.getConstructor(Test.class, DefaultTestFilter.class, Instantiator.class, ClassLoaderCache.class);

                    return (TestNGTestFramework) constructor.newInstance(template.task, failedTestsFilter, template.instantiator, classLoaderCache);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        static void copyOptions(TestNGOptions source, TestNGOptions target) {
            target.setOutputDirectory(source.getOutputDirectory());
            target.setIncludeGroups(source.getIncludeGroups());
            target.setExcludeGroups(source.getExcludeGroups());
            target.setConfigFailurePolicy(source.getConfigFailurePolicy());
            target.setListeners(source.getListeners());
            target.setParallel(source.getParallel());
            target.setThreadCount(source.getThreadCount());
            target.setUseDefaultListeners(source.getUseDefaultListeners());
            target.setSuiteName(source.getSuiteName());
            target.setTestName(source.getTestName());
            target.setSuiteXmlFiles(source.getSuiteXmlFiles());
            target.setPreserveOrder(source.getPreserveOrder());
            target.setGroupByInstances(source.getGroupByInstances());
            target.setSuiteXmlWriter(source.getSuiteXmlWriter());
            target.setSuiteXmlBuilder(source.getSuiteXmlBuilder());
        }

        static TestFrameworkProvider testFrameworkProvider(TestFrameworkTemplate template, TestFramework testFramework) {
            if (gradleVersionIsAtLeast("8.0")) {
                return new ProviderForCurrentGradleVersion(testFramework);
            } else if (gradleVersionIsAtLeast("6.6")) {
                return new ProviderForGradleOlderThanV8(template);
            } else {
                return new ProviderForGradleOlderThanV66(template);
            }
        }

    }

}
