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
import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.*;

public class ChainVersionMatcher implements VersionMatcher {
    private final LinkedList<VersionMatcher> matchers = Lists.newLinkedList();

    public void add(VersionMatcher matcher) {
        matchers.add(matcher);
    }

    public String getName() {
        return "chain";
    }

    public boolean isDynamic(ModuleVersionIdentifier module) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(module)) {
                return true;
            }
        }
        return false;
    }

    public int compare(ModuleVersionIdentifier requested, ModuleVersionIdentifier found, Comparator comparator) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requested)) {
                return matcher.compare(requested, found, comparator);
            }
        }
        throw new IllegalArgumentException("cannot compare module versions because requested version is not dynamic: " + requested);
    }

    public boolean accept(ModuleVersionIdentifier requested, ModuleVersionIdentifier found) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requested)) {
                return matcher.accept(requested, found);
            }
        }
        return matchers.getLast().accept(requested, found);
    }

    public boolean needModuleMetadata(ModuleVersionIdentifier requested, ModuleVersionIdentifier found) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requested)) {
                return matcher.needModuleMetadata(requested, found);
            }
        }
        return matchers.getLast().needModuleMetadata(requested, found);
    }

    public boolean accept(ModuleVersionIdentifier requested, ModuleVersionMetaData found) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.isDynamic(requested)) {
                return matcher.accept(requested, found);
            }
        }
        return matchers.getLast().accept(requested, found);
    }
}

