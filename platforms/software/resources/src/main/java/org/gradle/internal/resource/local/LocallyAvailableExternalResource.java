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

package org.gradle.internal.resource.local;

import org.gradle.internal.resource.ExternalResource;

import java.io.File;

/**
 * Represents an external resource whose meta-data and content is available locally. The content and meta-data may be a copy of some original resource and the original may or may not be a local resource.
 */
public interface LocallyAvailableExternalResource extends ExternalResource {
    /**
     * Returns a local file containing the content of this resource. This may nor may not be the original resource.
     */
    File getFile();

    /**
     * Does this resource currently exist?
     */
    boolean exists();
}
