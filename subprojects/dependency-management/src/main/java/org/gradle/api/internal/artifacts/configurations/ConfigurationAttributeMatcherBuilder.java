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

import com.google.common.base.Function;
import org.gradle.api.artifacts.ConfigurationAttributeMatcher;

import java.util.Comparator;

public class ConfigurationAttributeMatcherBuilder {
    private static final StrictAttributeValueMatch STRICT_ATTRIBUTE_VALUE_MATCH = new StrictAttributeValueMatch();
    private static final Function<String, String> NO_DEFAULT_VALUE = new Function<String, String>() {
        @Override
        public String apply(String input) {
            return null;
        }
    };

    private Comparator<String> comparator = STRICT_ATTRIBUTE_VALUE_MATCH;
    private Function<String, String> defaultValueBuilder = NO_DEFAULT_VALUE;

    private ConfigurationAttributeMatcherBuilder() {
    }

    public static ConfigurationAttributeMatcherBuilder newBuilder() {
        return new ConfigurationAttributeMatcherBuilder();
    }

    ConfigurationAttributeMatcher build() {
        return new ConstructedMatcher(comparator, defaultValueBuilder);
    }

    /**
     * A {@link Comparator} and a {@link ConfigurationAttributeMatcher} have slightly different semantics with regards to matching strings.
     * This method adapts a comparator to a configuration attribute matcher, expecting the comparator to break the contract of comparators
     * but respect the semantics of attribute matchers. In particular, attribute matchers are expected to return 0 for "match", "-1" for
     * no match and any positive distance for matches which are not perfect.
     * @param comparator sets the attribute value comparator.
     * @return this builder
     */
    public ConfigurationAttributeMatcherBuilder withComparator(Comparator<String> comparator) {
        this.comparator = comparator;
        return this;
    }

    public ConfigurationAttributeMatcherBuilder withDefaultValue(Function<String, String> defaultValueBuilder) {
        this.defaultValueBuilder = defaultValueBuilder;
        return this;
    }

    private static class ConstructedMatcher implements ConfigurationAttributeMatcher {
        private final Comparator<String> comparator;
        private final Function<String, String> defaultValue;

        private ConstructedMatcher(Comparator<String> comparator, Function<String, String> defaultValue) {
            this.comparator = comparator;
            this.defaultValue = defaultValue;
        }

        @Override
        public int score(String requestedValue, String attributeValue) {
            return comparator.compare(requestedValue, attributeValue);
        }

        @Override
        public String defaultValue(String requestedValue) {
            return defaultValue.apply(requestedValue);
        }
    }

    private static class StrictAttributeValueMatch implements Comparator<String> {
        @Override
        public int compare(String requested, String provided) {
            return (requested==null && provided==null)
                || (requested!=null && requested.equals(provided)) ? 0 : -1;
        }
    }
}
