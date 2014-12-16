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

import com.google.common.collect.Maps;
import org.apache.ivy.plugins.matcher.*;

import java.util.Map;

public class ResolverStrategy {
    private final VersionSelectorScheme versionSelectorScheme;
    private final Map<String, PatternMatcher> matchers = Maps.newHashMap();
    private final DefaultVersionComparator versionComparator = new DefaultVersionComparator();

    public ResolverStrategy() {
        versionSelectorScheme = new DefaultVersionSelectorScheme();

        addMatcher(ExactPatternMatcher.INSTANCE);
        addMatcher(RegexpPatternMatcher.INSTANCE);
        addMatcher(ExactOrRegexpPatternMatcher.INSTANCE);
        addMatcher(GlobPatternMatcher.INSTANCE);
    }

    private void addMatcher(PatternMatcher instance) {
        matchers.put(instance.getName(), instance);
    }

    public VersionSelectorScheme getVersionSelectorScheme() {
        return versionSelectorScheme;
    }

    public VersionComparator getVersionComparator() {
        return versionComparator;
    }

    public PatternMatcher getPatternMatcher(String name) {
        return matchers.get(name);
    }
}
