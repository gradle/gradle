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

package org.gradle.internal.metaobject;

import org.gradle.api.NonNullApi;

/**
 * An implementation of a Groovy MetaClass that is instrumented and might need to cancel some optimizations
 * in order to get the instrumentation to work properly.
 */
@NonNullApi
public interface InstrumentedMetaClass {
    /**
     * Checks if accessing a property by the specified name would be intercepted by the instrumentation at this
     * specific moment. The implementation may return different values at different times.
     */
    boolean interceptsPropertyAccess(String propertyName);
}
