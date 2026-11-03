/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import java.util.Collections;
import java.util.Set;

public class Permits {
    private static final Permits NONE = new Permits(Collections.emptySet());

    private final Set<String> allowedExtensions;

    public Permits(Set<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public static Permits none() {
        return NONE;
    }

    /**
     * Returns the list of extension names which can be used
     * in a restricted block like the "plugins" block
     */
    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }
}
