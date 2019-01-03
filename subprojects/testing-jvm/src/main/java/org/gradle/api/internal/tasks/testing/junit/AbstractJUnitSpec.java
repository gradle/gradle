/*
 * Copyright 2018 the original author or authors.
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

import java.io.Serializable;
import java.util.Set;

public abstract class AbstractJUnitSpec implements Serializable {
    private final Set<String> includedTests;
    private final Set<String> excludedTests;
    private final Set<String> includedTestsCommandLine;

    public AbstractJUnitSpec(
        Set<String> includedTests,
        Set<String> excludedTests,
        Set<String> includedTestsCommandLine
    ) {
        this.includedTests = includedTests;
        this.excludedTests = excludedTests;
        this.includedTestsCommandLine = includedTestsCommandLine;
    }

    public Set<String> getIncludedTests() {
        return includedTests;
    }

    public Set<String> getExcludedTests() {
        return excludedTests;
    }

    public Set<String> getIncludedTestsCommandLine() {
        return includedTestsCommandLine;
    }
}
