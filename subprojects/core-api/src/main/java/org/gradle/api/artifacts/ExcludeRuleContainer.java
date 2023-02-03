/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.artifacts;

import java.util.Map;
import java.util.Set;

/**
 * <p>A container for adding exclude rules for dependencies.</p>
 */
public interface ExcludeRuleContainer {
    /**
     * Returns all the exclude rules added to this container. If no exclude rules has been added an empty list is
     * returned.
     */
    Set<ExcludeRule> getRules();

    /**
     * Adds an exclude rule to this container. The ExcludeRule object gets created internally based on the map values
     * passed to this method. The possible keys for the map are:
     *
     * <ul>
     * <li><code>group</code> - The exact name of the organization or group that should be excluded.
     * <li><code>module</code> - The exact name of the module that should be excluded
     * </ul>
     *
     * @param args A map describing the exclude pattern.
     */
    void add(Map<String, String> args);
}
