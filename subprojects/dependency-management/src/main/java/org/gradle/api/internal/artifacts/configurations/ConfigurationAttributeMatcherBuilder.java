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

import org.gradle.api.artifacts.ConfigurationAttributeMatcher;

import java.util.Comparator;

public class ConfigurationAttributeMatcherBuilder {
    private static final StrictAttributeValueMatch STRICT_ATTRIBUTE_VALUE_MATCH = new StrictAttributeValueMatch();

    private boolean isRequired;
    private Comparator<String> comparator = STRICT_ATTRIBUTE_VALUE_MATCH;

    private ConfigurationAttributeMatcherBuilder() {
    }

    public static ConfigurationAttributeMatcherBuilder newBuilder() {
        return new ConfigurationAttributeMatcherBuilder();
    }

    ConfigurationAttributeMatcher build() {
        return new ConstructedMatcher(isRequired, comparator);
    }

    public ConfigurationAttributeMatcherBuilder required() {
        isRequired = true;
        return this;
    }

    public ConfigurationAttributeMatcherBuilder withComparator(Comparator<String> comparator) {
        this.comparator = comparator;
        return this;
    }

    private static class ConstructedMatcher implements ConfigurationAttributeMatcher {
        private final boolean isRequired;
        private final Comparator<String> comparator;

        private ConstructedMatcher(boolean isRequired, Comparator<String> comparator) {
            this.isRequired = isRequired;
            this.comparator = comparator;
        }

        @Override
        public boolean isRequired() {
            return isRequired;
        }

        @Override
        public int score(String requestedValue, String attributeValue) {
            return comparator.compare(requestedValue, attributeValue);
        }
    }

    private static class StrictAttributeValueMatch implements Comparator<String> {
        @Override
        public int compare(String required, String provided) {
            return (required==null && provided==null)
                || (required!=null && required.equals(provided)) ? 0 : -1;
        }
    }
}
