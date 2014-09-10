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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

import java.util.Comparator;

/**
 * Version matcher for dynamic version selectors ending in '+'.
 */
public class SubVersionMatcher implements VersionMatcher {
    private final Comparator<String> staticVersionComparator;

    public SubVersionMatcher(VersionMatcher staticVersionComparator) {
        this.staticVersionComparator = staticVersionComparator;
    }

    public boolean canHandle(String selector) {
        return selector.endsWith("+");
    }

    public boolean isDynamic(String selector) {
        return true;
    }

    public boolean needModuleMetadata(String selector) {
        return false;
    }

    public boolean accept(String selector, String candidate) {
        String prefix = selector.substring(0, selector.length() - 1);
        return candidate.startsWith(prefix);
    }

    public boolean accept(String selector, ModuleVersionMetaData candidate) {
        return accept(selector, candidate.getId().getVersion());
    }

    public int compare(String selector, String candidate) {
        if (accept(selector, candidate)) {
            return 1;
        }
        return staticVersionComparator.compare(selector, candidate);
    }
}
