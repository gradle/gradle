/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.report;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.tasks.testing.junit.result.LegacyResultsHelper;
import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestFailure;
import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;

import java.util.*;

import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE;
import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

/**
 * The model for the test report.
 */
public class AllTestResults extends CompositeTestResults {
    public static AllTestResults loadModelFromProvider(TestResultsProvider resultsProvider) {
        final AllTestResults model = new AllTestResults();
        LegacyResultsHelper.visitClasses(resultsProvider, provider -> {
            model.addTestClass(provider, provider.getResult().getName(), provider.getResult().getDisplayName());
            provider.visitChildren(childProvider -> {
                PersistentTestResult collectedResult = childProvider.getResult();
                String className = provider.getResult().getName();
                String classDisplayName = provider.getResult().getDisplayName();
                if (collectedResult.getLegacyProperties() != null) {
                    className = collectedResult.getLegacyProperties().getClassName();
                    classDisplayName = collectedResult.getLegacyProperties().getClassDisplayName();
                }
                final TestResult testResult = model.addTest(provider, className, classDisplayName, childProvider, collectedResult.getName(), collectedResult.getDisplayName(), collectedResult.getDuration());
                if (collectedResult.getResultType() == SKIPPED) {
                    testResult.setIgnored();
                } else if (collectedResult.getResultType() == FAILURE) {
                    testResult.setFailed();
                }
                // Always add failures even if not failed, may be used in SKIPPED tests
                List<PersistentTestFailure> failures = collectedResult.getFailures();
                for (PersistentTestFailure failure : failures) {
                    testResult.addFailure(failure);
                }
            });
        });
        return model;
    }

    private final Map<String, PackageTestResults> packages = new TreeMap<String, PackageTestResults>();

    public AllTestResults() {
        super(null);
    }

    @Override
    public String getTitle() {
        return "Test Summary";
    }

    @Override
    public String getBaseUrl() {
        return "index.html";
    }

    public Collection<PackageTestResults> getPackages() {
        return packages.values();
    }

    public TestResult addTest(TestResultsProvider classProvider, String className, String classDisplayName, TestResultsProvider provider, String testName, String testDisplayName, long duration) {
        PackageTestResults packageResults = addPackageForClass(className);
        return addTest(packageResults.addTest(classProvider, className, classDisplayName, provider, testName, testDisplayName, duration));
    }

    public ClassTestResults addTestClass(TestResultsProvider classProvider, String className, String classDisplayName) {
        return addPackageForClass(classProvider.getResult().getName()).addClass(classProvider, className, classDisplayName);
    }

    private PackageTestResults addPackageForClass(String className) {
        String packageName = StringUtils.substringBeforeLast(className, ".");
        if (packageName.equals(className)) {
            packageName = "";
        }
        return addPackage(packageName);
    }

    private PackageTestResults addPackage(String packageName) {
        PackageTestResults packageResults = packages.get(packageName);
        if (packageResults == null) {
            packageResults = new PackageTestResults(packageName, this);
            packages.put(packageName, packageResults);
        }
        return packageResults;
    }
}
