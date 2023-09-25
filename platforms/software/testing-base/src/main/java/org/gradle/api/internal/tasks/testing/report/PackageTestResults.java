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

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * The test results for a given package.
 */
public class PackageTestResults extends CompositeTestResults {
    private static final String DEFAULT_PACKAGE = "default-package";
    private final String name;
    private final Map<String, ClassTestResults> classes = new TreeMap<String, ClassTestResults>();

    public PackageTestResults(String name, AllTestResults model) {
        super(model);
        this.name = name.length() == 0 ? DEFAULT_PACKAGE : name;
    }

    @Override
    public String getTitle() {
        return name.equals(DEFAULT_PACKAGE) ? "Default package" : ("Package " + name);
    }

    @Override
    public String getBaseUrl() {
        return "packages/" + name + ".html";
    }

    public String getName() {
        return name;
    }

    public Collection<ClassTestResults> getClasses() {
        return classes.values();
    }

    public TestResult addTest(long classId, String className, String testName, long duration) {
        return addTest(classId, className, className, testName, testName, duration);
    }

    public TestResult addTest(long classId, String className, String classDisplayName, String testName, String testDisplayName, long duration) {
        ClassTestResults classResults = addClass(classId, className, classDisplayName);
        return addTest(classResults.addTest(testName, testDisplayName, duration));
    }

    public ClassTestResults addClass(long classId, String className) {
        return addClass(classId, className, className);
    }

    public ClassTestResults addClass(long classId, String className, String classDisplayName) {
        ClassTestResults classResults = classes.get(className);
        if (classResults == null) {
            classResults = new ClassTestResults(classId, className, classDisplayName, this);
            classes.put(className, classResults);
        }
        return classResults;
    }
}
