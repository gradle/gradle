/*
 * Copyright 2018 the original author or authors.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachingVersionSelectorScheme implements VersionSelectorScheme {
    private final Map<String, VersionSelector> cachedSelectors = new ConcurrentHashMap<>();
    private final VersionSelectorScheme delegate;

    public CachingVersionSelectorScheme(VersionSelectorScheme delegate) {
        this.delegate = delegate;
    }

    @Override
    public VersionSelector parseSelector(String selectorString) {
        VersionSelector versionSelector = cachedSelectors.get(selectorString);
        if (versionSelector == null) {
            versionSelector = delegate.parseSelector(selectorString);
            cachedSelectors.put(selectorString, versionSelector);
        }
        return versionSelector;
    }

    @Override
    public String renderSelector(VersionSelector selector) {
        return delegate.renderSelector(selector);
    }

    @Override
    public VersionSelector complementForRejection(VersionSelector selector) {
        return delegate.complementForRejection(selector);
    }

}
