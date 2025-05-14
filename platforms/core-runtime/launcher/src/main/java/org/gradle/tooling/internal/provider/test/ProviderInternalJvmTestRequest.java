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

package org.gradle.tooling.internal.provider.test;

import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class ProviderInternalJvmTestRequest implements InternalJvmTestRequest, Serializable {
    private final String className;
    private final String methodName;

    public ProviderInternalJvmTestRequest(String className, @Nullable String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProviderInternalJvmTestRequest that = (ProviderInternalJvmTestRequest) o;

        if (className != null ? !className.equals(that.className) : that.className != null) {
            return false;
        }
        return !(methodName != null ? !methodName.equals(that.methodName) : that.methodName != null);
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
        return result;
    }
}
