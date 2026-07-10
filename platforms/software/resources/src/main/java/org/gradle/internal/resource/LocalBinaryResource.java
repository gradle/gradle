/*
 * Copyright 2017 the original author or authors.
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

import java.io.File;
import java.net.URI;

/**
 * Some binary resource available somewhere on the local file system.
 */
public interface LocalBinaryResource extends Resource, ReadableContent {
    URI getURI();

    String getBaseName();

    /**
     * Returns the file containing this resource. Note that the content of this resource may not be the same as the file (for example, the file may be compressed, or this resource may represent an entry in an archive file, or both)
     */
    File getContainingFile();
}
