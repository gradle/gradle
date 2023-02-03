/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource;

import org.gradle.api.Describable;

/**
 * Represents some resource that may have content.
 *
 * <p>This type is currently pretty much empty. It's here as a place to extract and reuse stuff from the various subtypes, which all stared off as separate hierarchies.
 */
public interface Resource extends Describable {
    /**
     * Returns a display name for this resource. This can be used in log and error messages.
     *
     * @return the display name
     */
    @Override
    String getDisplayName();
}
