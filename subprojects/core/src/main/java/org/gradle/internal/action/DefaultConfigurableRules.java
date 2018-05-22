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

package org.gradle.internal.action;

import com.google.common.collect.ImmutableList;

import java.util.List;

import static java.util.Collections.singletonList;

public class DefaultConfigurableRules<DETAILS> implements ConfigurableRules<DETAILS> {

    public static <T> ConfigurableRules<T> of(ConfigurableRule<T> unique) {
        return new DefaultConfigurableRules<T>(singletonList(unique));
    }

    private final List<ConfigurableRule<DETAILS>> configurableRules;
    private final boolean cacheable;

    public DefaultConfigurableRules(List<ConfigurableRule<DETAILS>> rules) {
        this.configurableRules = ImmutableList.copyOf(rules);

        cacheable = computeCacheable();
    }

    private boolean computeCacheable() {
        boolean isCacheable = false;
        for (ConfigurableRule<DETAILS> configurableRule : configurableRules) {
            if (configurableRule.isCacheable()) {
                isCacheable = true;
            }
        }
        return isCacheable;
    }

    @Override
    public List<ConfigurableRule<DETAILS>> getConfigurableRules() {
        return configurableRules;
    }

    @Override
    public boolean isCacheable() {
        return cacheable;
    }

    @Override
    public String toString() {
        return configurableRules.toString();
    }
}
