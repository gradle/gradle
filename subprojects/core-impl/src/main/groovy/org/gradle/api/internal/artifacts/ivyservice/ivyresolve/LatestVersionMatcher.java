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

public class LatestVersionMatcher implements VersionMatcher {
    public boolean isDynamic(String version) {
        return version.startsWith("latest.");
    }

    public boolean accept(String requestedVersion, String foundVersion) {
        return true;
    }

    public boolean needModuleMetadata(String requestedVersion, String foundVersion) {
        // TODO we shouldn't download descriptor when latest.lowestStatus is requested
        // one way to achieve this would be to split up this method into needStatus() and needStatusScheme()
        // in cases where only the latter was needed, we would only fire metadata rules but wouldn't download descriptor
        return true;
    }

    public boolean accept(String requestedVersion, ModuleVersionMetaData foundVersion) {
        String requestedStatus = requestedVersion.substring("latest.".length());
        int requestedIndex = foundVersion.getStatusScheme().indexOf(requestedStatus);
        int foundIndex = foundVersion.getStatusScheme().indexOf(foundVersion.getStatus());
        return requestedIndex >=0 && requestedIndex <= foundIndex;
    }

    public int compare(String requestedVersion, String foundVersion, Comparator<String> staticComparator) {
        return needModuleMetadata(requestedVersion, foundVersion) ? 0 : 1;
    }
}
