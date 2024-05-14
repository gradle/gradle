/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;
import java.util.Map;

@ServiceScope(Scope.Global.class)
public interface ImportsReader {
    /**
     * Returns the list of packages that are imported by default into each Gradle build script.
     * This list is only meant for concise presentation to the user (e.g. in documentation). For
     * machine consumption, use the {@link #getSimpleNameToFullClassNamesMapping()} method, as it
     * provides a direct mapping from each simple name to the qualified name of the class to use.
     */
    String[] getImportPackages();

    /**
     * Returns a mapping from simple to qualified class name, derived from
     * the packages returned by {@link #getImportPackages()}. For historical reasons,
     * some simple name match multiple qualified names. In those cases the first match
     * should be used when resolving a name in the DSL.
     */
    Map<String, List<String>> getSimpleNameToFullClassNamesMapping();
}
