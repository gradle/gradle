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
import org.gradle.internal.FileUtils;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Test results for a given class.
 */
public class ClassTestResults extends CompositeTestResults {
    private final long id;
    private final String name;
    private final String displayName;
    private final PackageTestResults packageResults;
    private final Set<TestResult> results = new TreeSet<TestResult>();
    private final String baseUrl;

    public ClassTestResults(long id, String name, PackageTestResults packageResults) {
        this(id, name, name, packageResults);
    }

    public ClassTestResults(long id, String name, String displayName, PackageTestResults packageResults) {
        super(packageResults);
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.packageResults = packageResults;
        baseUrl = "classes/" + FileUtils.toSafeFileName(name) + ".html";
    }

    public long getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return name.equals(displayName) ? "Class " + name : displayName;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getReportName() {
        if (displayName != null && !displayName.equals(name)) {
            return displayName;
        }
        return getSimpleName();
    }

    public String getSimpleName() {
        String simpleName = StringUtils.substringAfterLast(name, ".");
        if (simpleName.equals("")) {
            return name;
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
