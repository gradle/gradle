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

/**
 * Version matcher for "static" version selectors (1.0, 1.2.3, etc.).
 */
public class ExactVersionSelector extends AbstractStringVersionSelector {
    private final String version;

    public ExactVersionSelector(String selector) {
        super(selector);
        this.version = selector;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public boolean requiresMetadata() {
        return false;
    }

    @Override
    public boolean matchesUniqueVersion() {
        return true;
    }

    @Override
    public boolean accept(String candidate) {
        return version.isEmpty() || version.equals(candidate);
    }

    public static boolean isExact(String selector) {
        return selector != null && !VersionRangeSelector.ALL_RANGE.matcher(selector).matches() && !selector.endsWith("+") && !selector.startsWith("latest.");
    }
}
