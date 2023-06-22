/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.failure;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.TestFailure;

import java.lang.reflect.Method;
import java.util.List;

@NonNullApi
public abstract class FailureMapper {

    public boolean accepts(Class<?> cls) {
        if (getSupportedClassNames().contains(cls.getName())) {
            return true;
        }

        Class<?> superclass = cls.getSuperclass();
        if (superclass == null) {
            return false;
        } else {
            return accepts(superclass);
        }
    }

    protected abstract List<String> getSupportedClassNames();

    public abstract TestFailure map(Throwable throwable, ThrowableToFailureMapper mapper) throws Exception;

    // Utility methods ------------------------------------

    protected static <T> T invokeMethod(Object obj, String methodName, Class<T> targetClass) throws Exception {
        Method method = obj.getClass().getDeclaredMethod(methodName);
        return targetClass.cast(method.invoke(obj));
    }

}
