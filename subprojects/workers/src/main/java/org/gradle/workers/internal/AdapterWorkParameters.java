/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.workers.WorkParameters;

/**
 * This is used to bridge between the "old" worker api with untyped parameters and the typed
 * parameter api.  It allows us to maintain backwards compatibility at the api layer, but use
 * only typed parameters under the covers.  This can be removed once the old api is retired.
 */
public interface AdapterWorkParameters extends WorkParameters {
    void setImplementationClassName(String implementationClassName);
    String getImplementationClassName();

    void setParams(Object[] params);
    Object[] getParams();

    void setDisplayName(String displayName);
    String getDisplayName();
}
