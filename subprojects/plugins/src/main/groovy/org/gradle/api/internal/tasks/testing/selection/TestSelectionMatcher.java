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

import com.google.common.base.Splitter;
import org.gradle.api.tasks.testing.TestSelectionSpec;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class TestSelectionMatcher {

    private final Pattern classPattern;
    private final Pattern methodPattern;

    public TestSelectionMatcher(TestSelectionSpec included) {
        this.classPattern = preparePattern(included.getClassPattern());
        this.methodPattern = preparePattern(included.getMethodPattern());
    }

    private Pattern preparePattern(String input) {
        StringBuilder pattern = new StringBuilder();
        for (String s : Splitter.on('*').split(input)) {
            if (s.equals("")) {
                pattern.append(".*"); //replace wildcard '*' with '.*'
            } else {
                pattern.append(Pattern.quote(s)); //quote everything else
            }
        }
        return Pattern.compile(pattern.toString());
    }

    public boolean matchesTest(String className, String methodName) {
        return matchesClass(className) && matchesMethod(methodName);
    }

    public boolean matchesAnyMethodIn(Class cls) {
        assert cls != null;
        while (Object.class != cls) {
            Method[] allMethods = cls.getDeclaredMethods();
            for (Method m : allMethods) {
                if (matchesMethod(m.getName())) {
                    return true;
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private boolean matchesMethod(String methodName) {
        return methodPattern.matcher(methodName).matches();
    }

    public boolean matchesClass(String className) {
        return classPattern.matcher(className).matches();
    }
}