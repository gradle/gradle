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

import org.apache.ivy.plugins.matcher.*;

import java.util.HashMap;
import java.util.Map;

public class ResolverStrategy {
    public static final ResolverStrategy INSTANCE = new ResolverStrategy();

    private final LatestStrategy latestStrategy;
    private final VersionMatcher versionMatcher;
    private final Map<String, PatternMatcher> matchers = new HashMap<String, PatternMatcher>();

    private ResolverStrategy() {
        LatestVersionStrategy latest = new LatestVersionStrategy();
        latestStrategy = latest;

        ChainVersionMatcher chain = new ChainVersionMatcher();
        chain.add(new VersionRangeMatcher(latest));
        chain.add(new SubVersionMatcher());
        chain.add(new LatestVersionMatcher());
        chain.add(new ExactVersionMatcher());
        versionMatcher = chain;

        latest.setVersionMatcher(versionMatcher);

        addMatcher(ExactPatternMatcher.INSTANCE);
        addMatcher(RegexpPatternMatcher.INSTANCE);
        addMatcher(ExactOrRegexpPatternMatcher.INSTANCE);
        addMatcher(GlobPatternMatcher.INSTANCE);
    }

    private void addMatcher(PatternMatcher instance) {
        matchers.put(instance.getName(), instance);
    }

    public VersionMatcher getVersionMatcher() {
        return versionMatcher;
    }

    public LatestStrategy getLatestStrategy() {
        return latestStrategy;
    }

    public PatternMatcher getPatternMatcher(String name) {
        return matchers.get(name);
    }
}
