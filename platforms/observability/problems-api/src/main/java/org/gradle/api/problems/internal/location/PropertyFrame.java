/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems.internal.location;

import org.gradle.internal.code.Locality;

/**
 * A property of a type, such as an input of a task type.
 */
public final class PropertyFrame extends Frame {

    private final String name;

    public PropertyFrame(String name, Locality locality) {
        this("property", name, locality);
    }

    /**
     * @param kind what sort of property this is, as shown to the user: "field", "input property"
     */
    public PropertyFrame(String kind, String name, Locality locality) {
        super(kind + " " + name, locality);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
