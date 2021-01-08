/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal;

/**
 * Provides meta-data about the current process. Generally used for logging and error messages.
 */
public interface ProcessMetaDataProvider {
    /**
     * Returns a unique identifier for this process. Should be unique across all processes on the local machine.
     */
    String getProcessIdentifier();

    /**
     * Returns a display name for this process. Should allow a human to figure out which process the display name refers to.
     */
    String getProcessDisplayName();
}
