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
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.internal.FileUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Test results for a given class.
 */
public class ClassTestResults extends CompositeTestResults {
    private final Set<TestResultsProvider> providers;
    private final String className;
    private final String classDisplayName;
    private final PackageTestResults packageResults;
    private final List<TestResult> results = new ArrayList<>();
    private final String baseUrl;

    public ClassTestResults(TestResultsProvider provider, String className, String classDisplayName, PackageTestResults packageResults) {
        super(packageResults);
        this.className = className;
        this.classDisplayName = classDisplayName;
        this.providers = new LinkedHashSet<>();
        this.providers.add(provider);
        this.packageResults = packageResults;
        baseUrl = "classes/" + FileUtils.toSafeFileName(className) + ".html";
    }

    public void addProvider(TestResultsProvider provider) {
        providers.add(provider);
    }

    public Collection<TestResultsProvider> getProviders() {
        return providers;
    }

    @Override
    public String getTitle() {
        return getName().equals(getDisplayName()) ? "Class " + getName() : getDisplayName();
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    public String getName() {
        return className;
    }

    public String getDisplayName() {
        return classDisplayName;
    }

    public String getReportName() {
        if (getDisplayName() != null && !getDisplayName().equals(getName())) {
            return getDisplayName();
        }
        return getSimpleName();
    }

    public String getSimpleName() {
        String simpleName = StringUtils.substringAfterLast(getName(), ".");
        if (simpleName.isEmpty()) {
            return getName();
        }
        return simpleName;
    }

    public PackageTestResults getPackageResults() {
        return packageResults;
    }

    public List<TestResult> getTestResults() {
        return results;
    }

    public TestResult addTest(TestResultsProvider provider, String testName, String testDisplayName, long duration) {
        TestResult test = new TestResult(provider, testName, testDisplayName, duration, this);
        results.add(test);
        return addTest(test);
    }
}
