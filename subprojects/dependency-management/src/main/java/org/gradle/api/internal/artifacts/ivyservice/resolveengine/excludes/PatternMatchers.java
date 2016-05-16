/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import com.google.common.collect.Maps;
import org.apache.ivy.plugins.matcher.ExactOrRegexpPatternMatcher;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.matcher.GlobPatternMatcher;
import org.apache.ivy.plugins.matcher.PatternMatcher;
import org.apache.ivy.plugins.matcher.RegexpPatternMatcher;

import java.util.Map;

public class PatternMatchers {
    /**
     * 'exact' pattern matcher name
     */
    public static final String EXACT = PatternMatcher.EXACT;

    /**
     * Any expression string: '*'
     */
    public static final String ANY_EXPRESSION = "*";

    private static PatternMatchers instance;

    private final Map<String, PatternMatcher> matchers = Maps.newHashMap();

    private PatternMatchers() {
        addMatcher(ExactPatternMatcher.INSTANCE);
        addMatcher(RegexpPatternMatcher.INSTANCE);
        addMatcher(ExactOrRegexpPatternMatcher.INSTANCE);
        addMatcher(GlobPatternMatcher.INSTANCE);
    }

    private void addMatcher(PatternMatcher instance) {
        matchers.put(instance.getName(), instance);
    }

    public PatternMatcher getMatcher(String name) {
        return matchers.get(name);
    }

    public static boolean isExactMatcher(String name) {
        return EXACT.equals(name);
    }

    public static synchronized PatternMatchers getInstance() {
        if (instance == null) {
            instance = new PatternMatchers();
        }
        return instance;
    }
}
