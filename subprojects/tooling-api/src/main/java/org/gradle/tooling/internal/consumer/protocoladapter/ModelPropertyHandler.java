/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.tooling.internal.consumer.protocoladapter;

import java.lang.reflect.Method;

public interface ModelPropertyHandler {
    /**
     * @param method getter for the property
     * @param delegate object that contain the property
     * @return whether this handler should provide the return value for given property.
     */
    boolean shouldHandle(Method method, Object delegate);

    Object getPropertyValue(Method method, Object delegate);
}
