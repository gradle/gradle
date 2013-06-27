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

package org.gradle.api.internal.coerce;

/**
 * Potentially transforms arguments to call a method with.
 */
public interface MethodArgumentsTransformer {

    /**
     * Transforms an argument list to call a method with.
     *
     * May return {@code args} if no transform is necessary.
     *
     * @param target The object to call the method on
     * @param methodName The name of the method to call
     * @param args The args to call the method with
     * @return The args transformed, or args. Never null.
     */
    Object[] transform(Object target, String methodName, Object... args);

}
