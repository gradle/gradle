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
package org.gradle.api.internal.tasks.testing.report.generic;

import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.internal.FileUtils;

import java.util.Set;
import java.util.TreeSet;

/**
 * Test results for a given group that isn't the root.
 */
public class GroupTestResults extends GenericCompositeTestResults {
    private final Set<GenericTestResultModel> children = new TreeSet<>();
    private final String baseUrl;

    public GroupTestResults(String baseBaseUrl, TestResultsProvider provider, GenericCompositeTestResults parentResults) {
        super(provider, parentResults);
        baseUrl = baseBaseUrl + "/" + FileUtils.toSafeFileName(provider.getResult().getName()) + "/index.html";
    }

    @Override
    public String getTitle() {
        return getName().equals(getDisplayName()) ? "Group " + getName() : getDisplayName();
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    public Set<GenericTestResultModel> getChildren() {
        return children;
    }

    public GenericTestResultModel addChild(String testName, String testDisplayName, long duration) {
        GenericTestResult test = new GenericTestResult(testName, testDisplayName, duration, this);
        children.add(test);
        return addChild(test);
    }
}
