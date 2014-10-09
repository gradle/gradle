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

import com.google.common.collect.Lists;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;

import java.util.*;

public class ChainVersionMatcher implements VersionMatcher {
    private final List<VersionMatcher> matchers = Lists.newArrayList();

    public void add(VersionMatcher matcher) {
        matchers.add(matcher);
    }

    public boolean canHandle(String selector) {
        // not expected to be called
        throw new UnsupportedOperationException("canHandle");
    }

    public boolean isDynamic(String selector) {
        return getCompatibleMatcher(selector).isDynamic(selector);
    }

    public boolean needModuleMetadata(String selector) {
        return getCompatibleMatcher(selector).needModuleMetadata(selector);
    }

    public boolean matchesUniqueVersion(String selector) {
        return getCompatibleMatcher(selector).matchesUniqueVersion(selector);
    }

    public boolean accept(String selector, String candidate) {
        return getCompatibleMatcher(selector).accept(selector, candidate);
    }

    public boolean accept(String selector, ModuleComponentResolveMetaData candidate) {
        return getCompatibleMatcher(selector).accept(selector, candidate);
    }

    public int compare(String selector, String candidate) {
        return getCompatibleMatcher(selector).compare(selector, candidate);
    }

    private VersionMatcher getCompatibleMatcher(String selector) {
        for (VersionMatcher matcher : matchers) {
            if (matcher.canHandle(selector)) {
                return matcher;
            }
        }
        throw new IllegalArgumentException("Invalid version selector: " + selector);
    }
}

