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

    public boolean isDynamic(String selector) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(selector)) {
                return true;
            }
        }
        return false;
    }

    public boolean accept(String selector, String candidate) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(selector)) {
                return matcher.accept(selector, candidate);
            }
        }
        return matchers.getLast().accept(selector, candidate);
    }

    public boolean needModuleMetadata(String selector, String candidate) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(selector)) {
                return matcher.needModuleMetadata(selector, candidate);
            }
        }
        return matchers.getLast().needModuleMetadata(selector, candidate);
    }

    public boolean accept(String selector, ModuleVersionMetaData candidate) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(selector)) {
                return matcher.accept(selector, candidate);
            }
        }
        return matchers.getLast().accept(selector, candidate);
    }

    public int compare(String selector, String candidate, Comparator<String> candidateComparator) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(selector)) {
                return matcher.compare(selector, candidate, candidateComparator);
            }
        }
        throw new IllegalArgumentException("Cannot compare versions because requested version is not dynamic: " + selector);
    }
}

