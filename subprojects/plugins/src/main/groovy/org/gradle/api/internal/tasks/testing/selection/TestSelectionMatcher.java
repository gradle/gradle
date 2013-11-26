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
package org.gradle.api.internal.tasks.testing.selection;

import org.gradle.api.tasks.testing.TestSelectionSpec;

import java.lang.reflect.Method;

public class TestSelectionMatcher {

    private TestSelectionSpec included;

    public TestSelectionMatcher(TestSelectionSpec included) {
        this.included = included;
    }

    public boolean matchesTest(String className, String methodName) {
        return matchesClass(className) && methodName.matches(included.getMethodPattern());
    }

    public boolean matchesClass(String className) {
        return className.matches(included.getClassPattern());
    }

    public boolean matchesAnyMethodIn(Class cls) {
        assert cls != null;
        while (Object.class != cls) {
            Method[] allMethods = cls.getDeclaredMethods();
            for (Method m : allMethods) {
                if (m.getName().matches(included.getMethodPattern())) {
                    return true;
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }
}