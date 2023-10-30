/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.ArrayList;
import java.util.List;

public class NoMatchingCapabilitiesException extends RuntimeException implements ResolutionProvider {
    private final List<String> resolutions = new ArrayList<>(2); // usually only one resolution

    public NoMatchingCapabilitiesException(String message, DocumentationRegistry documentationRegistry) {
        super(message);
        addResolution("Review the variant matching algorithm documentation at " + documentationRegistry.getDocumentationFor("variant_attributes", "abm_algorithm"));
    }

    /**
     * Adds a resolution to the list of resolutions.
     *
     * Meant to be called during subclass construction, so <strong>must</strong> remain safe to do so by only accessing fields on this type.
     * @param resolution The resolution (suggestion message) to add
     */
    public void addResolution(String resolution) {
        resolutions.add(resolution);
    }

    @Override
    public ImmutableList<String> getResolutions() {
        return ImmutableList.copyOf(resolutions);
    }
}
