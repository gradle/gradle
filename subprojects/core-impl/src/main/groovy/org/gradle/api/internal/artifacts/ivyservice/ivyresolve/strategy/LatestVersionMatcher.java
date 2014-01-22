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

public class LatestVersionMatcher implements VersionMatcher {
    public boolean canHandle(String selector) {
        return selector.startsWith("latest.");
    }

    public boolean isDynamic(String selector) {
        return true;
    }

    public boolean needModuleMetadata(String selector) {
        return true;
    }

    public boolean accept(String selector, String candidate) {
        throw new UnsupportedOperationException("accept(String, String)");
    }

    public boolean accept(String selector, ModuleVersionMetaData candidate) {
        String selectorStatus = selector.substring("latest.".length());
        int selectorStatusIndex = candidate.getStatusScheme().indexOf(selectorStatus);
        int candidateStatusIndex = candidate.getStatusScheme().indexOf(candidate.getStatus());
        return selectorStatusIndex >=0 && selectorStatusIndex <= candidateStatusIndex;
    }

    public int compare(String selector, String candidate) {
        return 0;
    }
}
