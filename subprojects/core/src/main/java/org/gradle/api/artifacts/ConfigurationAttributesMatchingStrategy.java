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
package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Transformer;

import java.util.Comparator;

/**
 * A configuration matching strategy determines how configuration attributes are matched from a
 * source configuration to a target configuration. The strategy allows defining how attributes
 * are matched per attribute name. Strategies are attached to a {@link Configuration}.
 */
@Incubating
public interface ConfigurationAttributesMatchingStrategy {
    /**
     * Returns the attribute matcher for a specific attribute name.
     * @param attributeName the name of the attribute
     * @return the matcher for this attribute. Must never be null.
     */
    ConfigurationAttributeMatcher getAttributeMatcher(String attributeName);

    /**
     * Sets the attribute matcher for a particular attribute.
     * @param attributeName the name of the attribute.
     * @param matcher the matcher for this attribute
     */
    void setAttributeMatcher(String attributeName, ConfigurationAttributeMatcher matcher);

    /**
     * Configures an attribute matcher using a convenient builder.
     * @param attributeName the name of the attribute to configure a matcher for.
     * @param configureAction the matcher configuration
     */
    void attributeMatcher(String attributeName, Action<? super ConfigurationAttributeMatcherBuilder> configureAction);

    /**
     * A configuration attribute matcher builder is responsible for configuring a matcher.
     */
    interface ConfigurationAttributeMatcherBuilder {

        /**
         * A {@link Comparator} and a {@link ConfigurationAttributeMatcher} have slightly different semantics with regards to matching strings.
         * This method adapts a comparator to a configuration attribute matcher, expecting the comparator to break the contract of comparators
         * but respect the semantics of attribute matchers. In particular, attribute matchers are expected to return 0 for "match", "-1" for
         * no match and any positive distance for matches which are not perfect.
         * @param comparator sets the attribute value comparator.
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder withComparator(Comparator<String> comparator);

        /**
         * Sets the function which computes the default value of an attribute in case it is missing.
         * @param defaultValueBuilder the value builder
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder withDefaultValue(Transformer<String, String> defaultValueBuilder);

        /**
         * Builds the matcher.
         * @return implementations are expected to return an immutable matcher.
         */
        ConfigurationAttributeMatcher build();

    }
}
