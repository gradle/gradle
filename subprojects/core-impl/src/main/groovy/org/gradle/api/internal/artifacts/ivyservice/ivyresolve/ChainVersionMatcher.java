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

import com.google.common.collect.Lists;

import java.util.*;

public class ChainVersionMatcher implements VersionMatcher {
    private final LinkedList<VersionMatcher> matchers = Lists.newLinkedList();

    public void add(VersionMatcher matcher) {
        matchers.add(matcher);
    }

    public boolean isDynamic(String version) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(version)) {
                return true;
            }
        }
        return false;
    }

    public boolean accept(String requestedVersion, String foundVersion) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requestedVersion)) {
                return matcher.accept(requestedVersion, foundVersion);
            }
        }
        return matchers.getLast().accept(requestedVersion, foundVersion);
    }

    public boolean needModuleMetadata(String requestedVersion, String foundVersion) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requestedVersion)) {
                return matcher.needModuleMetadata(requestedVersion, foundVersion);
            }
        }
        return matchers.getLast().needModuleMetadata(requestedVersion, foundVersion);
    }

    public boolean accept(String requestedVersion, ModuleVersionMetaData foundVersion) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requestedVersion)) {
                return matcher.accept(requestedVersion, foundVersion);
            }
        }
        return matchers.getLast().accept(requestedVersion, foundVersion);
    }

    public int compare(String requestedVersion, String foundVersion, Comparator<String> staticComparator) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requestedVersion)) {
                return matcher.compare(requestedVersion, foundVersion, staticComparator);
            }
        }
        throw new IllegalArgumentException("Cannot compare versions because requested version is not dynamic: " + requestedVersion);
    }
}

