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

package org.gradle.internal.classpath.intercept;

import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

/**
 * A call interceptor that can also tell about the type of the property that it intercepts access to.
 */
@NonNullApi
public interface PropertyAwareCallInterceptor {
    /**
     * Checks if the interceptor handles property access on instances of the specified receiver type.
     * @return If property access is intercepted, the type of the property. Null otherwise.
     */
    @Nullable
    Class<?> matchesProperty(Class<?> receiverClass);
}
