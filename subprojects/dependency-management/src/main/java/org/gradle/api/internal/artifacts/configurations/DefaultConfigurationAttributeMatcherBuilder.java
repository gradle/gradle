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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ConfigurationAttributeMatcher;
import org.gradle.api.artifacts.ConfigurationAttributeScorer;

public class DefaultConfigurationAttributeMatcherBuilder implements ConfigurationAttributesMatchingStrategyInternal.ConfigurationAttributeMatcherBuilderInternal {

    private ConfigurationAttributeScorer scorer = ConfigurationAttributeMatcher.STRICT_ATTRIBUTE_VALUE_MATCH;
    private Transformer<String, String> defaultValueBuilder = ConfigurationAttributeMatcher.NO_DEFAULT;

    private DefaultConfigurationAttributeMatcherBuilder() {
    }

    public static DefaultConfigurationAttributeMatcherBuilder newBuilder() {
        return new DefaultConfigurationAttributeMatcherBuilder();
    }

    public ConfigurationAttributeMatcher build() {
        return new ConstructedMatcher(scorer, defaultValueBuilder);
    }

    @Override
    public DefaultConfigurationAttributeMatcherBuilder withScorer(ConfigurationAttributeScorer comparator) {
        this.scorer = comparator;
        return this;
    }

    @Override
    public DefaultConfigurationAttributeMatcherBuilder withDefaultValue(Transformer<String, String> defaultValueBuilder) {
        this.defaultValueBuilder = defaultValueBuilder;
        return this;
    }

    private static class ConstructedMatcher implements ConfigurationAttributeMatcher {
        private final ConfigurationAttributeScorer scorer;
        private final Transformer<String, String> defaultValue;

        private ConstructedMatcher(ConfigurationAttributeScorer scorer, Transformer<String, String> defaultValue) {
            this.scorer = scorer;
            this.defaultValue = defaultValue;
        }

        @Override
        public int score(String requestedValue, String attributeValue) {
            return scorer.score(requestedValue, attributeValue);
        }

        @Override
        public String defaultValue(String requestedValue) {
            return defaultValue.transform(requestedValue);
        }
    }

}
