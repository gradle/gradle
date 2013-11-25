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

    private final String className;
    private final String methodName;

    public DefaultTestSelectionSpec(String className, String methodName) {
        assert className != null : "class name for included test cannot be null";
        assert methodName != null : "method name for included test cannot be null";
        this.className = className;
        this.methodName = methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }

    public boolean matchesClass(String className) {
        return this.className.equals(className);
    }

    public String toString() {
        return className + "." + methodName;
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

        if (!className.equals(that.className)) {
            return false;
        }
        if (!methodName.equals(that.methodName)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = className.hashCode();
        result = 31 * result + methodName.hashCode();
        return result;
    }
}
