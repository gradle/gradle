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

public class SubVersionMatcher implements VersionMatcher {
    public String getName() {
        return "sub-version";
    }

    public boolean isDynamic(String version) {
        return version.endsWith("+");
    }

    public boolean accept(String requestedVersion, String foundVersion) {
        String prefix = requestedVersion.substring(0, requestedVersion.length() - 1);
        return foundVersion.startsWith(prefix);
    }

    public int compare(String requestedVersion, String foundVersion, Comparator<String> staticComparator) {
        if (foundVersion.startsWith(requestedVersion.substring(0, requestedVersion.length() - 1))) {
            return 1;
        }
        return staticComparator.compare(requestedVersion, foundVersion);
    }

    public boolean needModuleMetadata(String requestedVersion, String foundVersion) {
        return false;
    }

    public boolean accept(String requestedVersion, ModuleVersionMetaData foundVersion) {
        return accept(requestedVersion, foundVersion.getId().getVersion());
    }
}
