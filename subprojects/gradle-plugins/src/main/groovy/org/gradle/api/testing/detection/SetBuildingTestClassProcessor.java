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
package org.gradle.api.testing.detection;

import org.gradle.api.testing.fabric.TestClassRunInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The Set building test class processor is used when running tests with Ant.
 *
 * All detected test classes are added to a set.
 *
 * @author Tom Eyckmans
 */
public class SetBuildingTestClassProcessor implements TestClassProcessor {

    private final Set<String> testClassNames;

    public SetBuildingTestClassProcessor() {
        this.testClassNames = new HashSet<String>();
    }

    public void processTestClass(TestClassRunInfo testClass) {
        testClassNames.add(testClass.getTestClassName().replace('.', '/') + ".class");
    }

    public Set<String> getTestClassFileNames() {
        return Collections.unmodifiableSet(testClassNames);
    }
}
