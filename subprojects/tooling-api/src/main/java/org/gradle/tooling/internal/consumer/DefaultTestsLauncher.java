/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import org.gradle.api.Incubating;
import org.gradle.tooling.TestsLauncher;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@Incubating
class DefaultTestsLauncher extends AbstractBuildLauncher<DefaultTestsLauncher> implements TestsLauncher {

    private final Set<String> testIncludePatterns = new LinkedHashSet<String>();

    public DefaultTestsLauncher(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters, connection);
        operationParamsBuilder.setTasks(Collections.<String>emptyList());
    }

    private void updatePatternList() {
        operationParamsBuilder.setTestIncludePatterns(new ArrayList<String>(testIncludePatterns));
    }

    @Override
    protected DefaultTestsLauncher getThis() {
        return this;
    }

    @Override
    public TestsLauncher addTestsByPattern(String... patterns) {
        Collections.addAll(testIncludePatterns, patterns);
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher addJvmTestClasses(String... testClasses) {
        for (String testClass : testClasses) {
            testIncludePatterns.add(testClass + ".*");
        }
        updatePatternList();
        return this;
    }

    @Override
    public TestsLauncher addJvmTestMethods(String testClass, String... methods) {
        for (String method : methods) {
            testIncludePatterns.add(testClass + "." + method);
        }
        updatePatternList();
        return this;
    }
}
