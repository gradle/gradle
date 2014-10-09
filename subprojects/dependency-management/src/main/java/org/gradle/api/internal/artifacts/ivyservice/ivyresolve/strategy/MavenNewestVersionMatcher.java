/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;

import java.util.regex.Pattern;

public class MavenNewestVersionMatcher implements VersionMatcher {

    public static final String RELEASE = "RELEASE";
    public static final String LATEST = "LATEST";
    private static final String SNAPSHOT = "SNAPSHOT";
    private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile("^(.*-)?([0-9]{8}\\.[0-9]{6}-[0-9]+)$");

    public boolean canHandle(String selector) {
        return selector.equals(RELEASE) || selector.equals(LATEST);
    }

    public boolean isDynamic(String selector) {
        return true;
    }

    public boolean needModuleMetadata(String selector) {
        return false;
    }

    // TODO:DAZ Maybe this is true?
    public boolean matchesUniqueVersion(String selector) {
        return false;
    }

    public boolean accept(String selector, String candidate) {
        if (selector.equals(RELEASE)) {
            return !isSnapshot(candidate);
        } else if (selector.equals(LATEST)) {
            return true;
        }
        //TODO Propably need a better name for this
        throw new IllegalArgumentException("Not Maven Newest: " + selector);
    }

    public boolean accept(String selector, ModuleComponentResolveMetaData candidate) {
        return accept(selector, candidate.getComponentId().getVersion());
    }

    public int compare(String selector, String candidate) {
        return 0;
    }

    private static boolean isSnapshot(String version) {
        return version.endsWith(SNAPSHOT) || SNAPSHOT_TIMESTAMP.matcher(version).matches();
    }
}
