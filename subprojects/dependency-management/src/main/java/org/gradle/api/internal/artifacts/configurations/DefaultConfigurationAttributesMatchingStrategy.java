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
package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ConfigurationAttributesMatchingStrategy;
import org.gradle.api.artifacts.ConfigurationAttributeMatcher;

import java.util.Map;

public class DefaultConfigurationAttributesMatchingStrategy implements ConfigurationAttributesMatchingStrategy {
    private final static ConfigurationAttributeMatcher REQUIRED = ConfigurationAttributeMatcherBuilder.newBuilder()
        .required()
        .build();

    private final static ConfigurationAttributeMatcher OPTIONAL = ConfigurationAttributeMatcherBuilder.newBuilder()
        .build();

    private final Map<String, ConfigurationAttributeMatcher> matchers = Maps.newHashMap();

    @Override
    public ConfigurationAttributeMatcher getAttributeMatcher(String attributeName) {
        ConfigurationAttributeMatcher attributeMatcher = matchers.get(attributeName);
        if (attributeMatcher != null) {
            return attributeMatcher;
        }
        return REQUIRED;
    }

    @Override
    public void setAttributeMatcher(String attributeName, ConfigurationAttributeMatcher matcher) {
        matchers.put(attributeName, matcher);
    }

    @Override
    public void optional(String attributeName) {
        setAttributeMatcher(attributeName,  OPTIONAL);
    }

    @Override
    public void required(String attributeName) {
        setAttributeMatcher(attributeName, REQUIRED);
    }

}
