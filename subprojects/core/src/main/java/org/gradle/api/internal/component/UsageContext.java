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

package org.gradle.api.internal.component;

import org.gradle.api.attributes.Usage;
import org.gradle.api.component.SoftwareComponentVariant;

/**
 * This is a legacy type and should be avoided if possible. Use {@link SoftwareComponentVariant} instead.
 */
public interface UsageContext extends SoftwareComponentVariant {
    @Deprecated
    default Usage getUsage(){
        // This method kept for backwards compatibility of plugins (like kotlin-multiplatform) using internal APIs.
        // KMP has recently stopped using this method, so we should be able to remove it
        throw new UnsupportedOperationException("This method has been deprecated and should never be called");
    }
}
