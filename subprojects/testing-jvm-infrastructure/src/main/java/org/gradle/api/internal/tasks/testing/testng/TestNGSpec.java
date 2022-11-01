/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;

import java.io.Serializable;
import java.util.Set;

public class TestNGSpec implements Serializable {
    private static final long serialVersionUID = 1;

    private final TestFilterSpec filter;
    private final String defaultSuiteName;
    private final String defaultTestName;
    private final String parallel;
    private final int threadCount;
    private final boolean useDefaultListener;
    private final Set<String> includeGroups;
    private final Set<String> excludeGroups;
    private final Set<String> listeners;
    private final String configFailurePolicy;
    private final boolean preserveOrder;
    private final boolean groupByInstances;

    public TestNGSpec(
        TestFilterSpec filter,
        String defaultSuiteName,
        String defaultTestName,
        String parallel,
        int threadCount,
        boolean useDefaultListener,
        Set<String> includeGroups,
        Set<String> excludeGroups,
        Set<String> listeners,
        String configFailurePolicy,
        boolean preserveOrder,
        boolean groupByInstances
    ) {
        this.filter = filter;
        this.defaultSuiteName = defaultSuiteName;
        this.defaultTestName = defaultTestName;
        this.parallel = parallel;
        this.threadCount = threadCount;
        this.useDefaultListener = useDefaultListener;
        this.includeGroups = includeGroups;
        this.excludeGroups = excludeGroups;
        this.listeners = listeners;
        this.configFailurePolicy = configFailurePolicy;
        this.preserveOrder = preserveOrder;
        this.groupByInstances = groupByInstances;
    }

    public TestFilterSpec getFilter() {
        return filter;
    }

    public Set<String> getListeners() {
        return listeners;
    }

    public Set<String> getExcludeGroups() {
        return excludeGroups;
    }

    public Set<String> getIncludeGroups() {
        return includeGroups;
    }

    public boolean getUseDefaultListeners() {
        return useDefaultListener;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public String getParallel() {
        return parallel;
    }

    public String getDefaultTestName() {
        return defaultTestName;
    }

    public String getDefaultSuiteName() {
        return defaultSuiteName;
    }

    public String getConfigFailurePolicy() {
        return configFailurePolicy;
    }

    public boolean getPreserveOrder() {
        return preserveOrder;
    }

    public boolean getGroupByInstances() {
        return groupByInstances;
    }
}
