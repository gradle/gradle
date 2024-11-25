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

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test results for a given class.
 */
public class ClassTestResults extends CompositeTestResults {
    private final TestResultsProvider provider;
    private final PackageTestResults packageResults;
    private final Set<TestResult> results = new TreeSet<>();
    private final String baseUrl;

    public ClassTestResults(TestResultsProvider provider, PackageTestResults packageResults) {
        super(packageResults);
        this.provider = provider;
        this.packageResults = packageResults;
        baseUrl = "classes/" + FileUtils.toSafeFileName(provider.getResult().getName()) + ".html";
    }

    public TestResultsProvider getProvider() {
        return provider;
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
        return provider.getResult().getName();
    }

    public String getDisplayName() {
        return provider.getResult().getDisplayName();
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

    public Collection<TestResult> getTestResults() {
        return results;
    }

    public TestResult addTest(String testName, String testDisplayName, long duration) {
        TestResult test = new TestResult(testName, testDisplayName, duration, this);
        results.add(test);
        return addTest(test);
    }
}
