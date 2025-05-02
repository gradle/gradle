/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.publish.ivy;

import org.gradle.api.artifacts.ivy.IvyExtraInfo;

/**
 * Represents a modifiable form of IvyExtraInfo so that "extra" info elements
 * can be configured on an Ivy publication.
 */
public interface IvyExtraInfoSpec extends IvyExtraInfo {

    /**
     * Puts the specified extra element into the list of extra info elements.
     *
     * @param namespace The namespace of the element to add
     * @param name The name of the element to add
     * @param value The value of the element to add
     */
    void add(String namespace, String name, String value);
}
