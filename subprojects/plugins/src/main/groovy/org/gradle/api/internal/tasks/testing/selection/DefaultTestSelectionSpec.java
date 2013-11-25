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

import java.io.Serializable;

public class DefaultTestSelectionSpec implements Serializable {

    private final String classPattern;
    private final String methodPattern;

    public DefaultTestSelectionSpec(String classPattern, String methodPattern) {
        assert classPattern != null : "class pattern for included test cannot be null";
        assert methodPattern != null : "method pattern for included test cannot be null";
        this.classPattern = classPattern;
        this.methodPattern = methodPattern;
    }

    public String getMethodPattern() {
        return methodPattern;
    }

    public String getClassPattern() {
        return classPattern;
    }

    public boolean matchesTest(String className, String methodName) {
        return className.matches(classPattern) && methodName.matches(methodPattern);
    }

    public boolean matchesClass(String className) {
        return this.classPattern.equals(className);
    }

    public String toString() {
        return "class: '" + classPattern + "', method: '" + methodPattern + "'";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultTestSelectionSpec)) {
            return false;
        }

        DefaultTestSelectionSpec that = (DefaultTestSelectionSpec) o;

        if (!classPattern.equals(that.classPattern)) {
            return false;
        }
        if (!methodPattern.equals(that.methodPattern)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = classPattern.hashCode();
        result = 31 * result + methodPattern.hashCode();
        return result;
    }
}
