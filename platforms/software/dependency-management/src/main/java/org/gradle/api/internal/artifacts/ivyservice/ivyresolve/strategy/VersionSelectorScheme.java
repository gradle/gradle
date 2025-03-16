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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Compares version selectors against candidate versions, indicating whether they match or not.
 *
 */
@ServiceScope(Scope.Build.class)
public interface VersionSelectorScheme {
    /**
     * Returns an appropriate {@link VersionSelector} for the given selector string.
     *
     * @param selectorString - the string representation of the selector
     * @return the {@link VersionSelector}
     */
    VersionSelector parseSelector(String selectorString);

    /**
     * Renders a {@link VersionSelector} to a selector string.
     */
    String renderSelector(VersionSelector selector);

    /**
     * Creates another version selector which complements the provided one, but also makes sense to use
     * in a rejection rule. It will therefore fail when computing a complement for this use case doesn't
     * make sense.
     * @param selector the selector to create a complement for
     * @return a selector that complements the provided one and can be used in a reject rule
     */
    VersionSelector complementForRejection(VersionSelector selector);
}
