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
 * Version matcher for dynamic version selectors ending in '+'.
 */
public class SubVersionSelector extends AbstractStringVersionSelector {
    private final String prefix;

    public SubVersionSelector(String selector) {
        super(selector);
        prefix = selector.substring(0, selector.length() - 1);
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public boolean requiresMetadata() {
        return false;
    }

    @Override
    public boolean matchesUniqueVersion() {
        return false;
    }

    @Override
    public boolean accept(String candidate) {
        return candidate.startsWith(prefix);
    }

    @Override
    public boolean canShortCircuitWhenVersionAlreadyPreselected() {
        return false;
    }

}
