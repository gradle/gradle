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

package org.gradle.api.resources;

import java.net.URI;

/**
 * A generic resource of some kind. Only describes the resource.
 * There are more specific interface that extend this one and specify ways of accessing the resource's content.
 */
public interface Resource {

    /**
     * Human readable name of this resource
     *
     * @return human readable name, should not be null
     */
    String getDisplayName();

    /**
     * Uniform resource identifier that uniquely describes this resource
     *
     * @return unique URI, should not be null
     */
    URI getURI();

    /**
     * Short name that concisely describes this resource
     *
     * @return concise base name, should not be null
     */
    String getBaseName();
}
