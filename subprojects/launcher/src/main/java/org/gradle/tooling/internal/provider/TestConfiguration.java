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

package org.gradle.tooling.internal.provider;

import org.gradle.tooling.internal.protocol.events.InternalJvmTestDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTestDescriptor;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class TestConfiguration implements Serializable {
    private static final String[] EMPTY_ARRAY = new String[0];

    private final String[] includePatterns;
    private final String[] excludePatterns;
    private final boolean alwaysRunTests;

    public TestConfiguration(List<String> testIncludePatterns, List<String> testExcludePatterns, List<? extends InternalTestDescriptor> descriptors, boolean alwaysRunTests) {
        this.alwaysRunTests = alwaysRunTests;
        List<String> includes = computeIncludePattern(testIncludePatterns, descriptors);
        this.includePatterns = includes.toArray(new String[includes.size()]);
        this.excludePatterns = testExcludePatterns == null ? EMPTY_ARRAY : testExcludePatterns.toArray(new String[testExcludePatterns.size()]);
    }

    public String[] getIncludePatterns() {
        return includePatterns;
    }

    public String[] getExcludePatterns() {
        return excludePatterns;
    }

    public boolean isAlwaysRunTests() {
        return alwaysRunTests;
    }

    private static List<String> computeIncludePattern(List<String> includes, List<? extends InternalTestDescriptor> descriptors) {
        List<String> result = new LinkedList<String>();
        if (includes != null) {
            result.addAll(includes);
        }
        if (descriptors != null) {
            for (InternalTestDescriptor descriptor : descriptors) {
                if (descriptor instanceof InternalJvmTestDescriptor) {
                    InternalJvmTestDescriptor jvmTest = (InternalJvmTestDescriptor) descriptor;
                    String className = jvmTest.getClassName();
                    String methodName = jvmTest.getMethodName();
                    String pattern = methodName == null ? className : className + "." + methodName;
                    result.add(pattern);
                }
            }
        }
        return result;
    }
}
