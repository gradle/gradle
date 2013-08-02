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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import java.util.Comparator;

/**
 * Version matcher for "static" version selectors (1.0, 1.2.3, etc.).
 */
public class ExactVersionMatcher implements VersionMatcher {
    public boolean isDynamic(String selector) {
        return false;
    }

    public boolean needModuleMetadata(String selector, String candidate) {
        return false;
    }

    public boolean accept(String selector, String candidate) {
        return selector.equals(candidate);
    }

    public boolean accept(String selector, ModuleVersionMetaData candidate) {
        return accept(selector, candidate.getId().getVersion());
    }

    public int compare(String selector, String candidate, Comparator<String> candidateComparator) {
        throw new UnsupportedOperationException("compare");
    }
}