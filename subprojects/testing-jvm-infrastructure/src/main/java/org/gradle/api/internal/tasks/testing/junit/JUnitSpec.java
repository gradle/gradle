/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;

import java.io.Serializable;
import java.util.Set;

public class JUnitSpec implements Serializable {
    private final TestFilterSpec filter;
    private final Set<String> includeCategories;
    private final Set<String> excludeCategories;

    public JUnitSpec(
        TestFilterSpec filter,
        Set<String> includeCategories,
        Set<String> excludeCategories
    ) {
        this.filter = filter;
        this.includeCategories = includeCategories;
        this.excludeCategories = excludeCategories;
    }

    public TestFilterSpec getFilter() {
        return filter;
    }

    public Set<String> getIncludeCategories() {
        return includeCategories;
    }

    public Set<String> getExcludeCategories() {
        return excludeCategories;
    }

    public boolean hasCategoryConfiguration() {
        return !(excludeCategories.isEmpty() && includeCategories.isEmpty());
    }
}
