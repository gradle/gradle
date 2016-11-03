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
import org.gradle.internal.HasInternalProtocol;

/**
 * A configuration matching strategy determines how configuration attributes are matched from a
 * source configuration to a target configuration. The strategy allows defining how attributes
 * are matched per attribute name. Strategies are attached to a {@link Configuration}.
 */
@Incubating
@HasInternalProtocol
public interface ConfigurationAttributesMatchingStrategy {

    /**
     * Configures an attribute matcher using a convenient builder.
     * @param attributeName the name of the attribute to configure a matcher for.
     * @param configureAction the matcher configuration
     */
    void matcher(String attributeName, Action<? super ConfigurationAttributeMatcherBuilder> configureAction);

    /**
     * A configuration attribute matcher builder is responsible for configuring a matcher.
     */
    @HasInternalProtocol
    interface ConfigurationAttributeMatcherBuilder {

        /**
         * This method adapts a scorer to a configuration attribute matcher.
         * @param comparator sets the attribute scorer.
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder setScorer(ConfigurationAttributeScorer comparator);

        /**
         * Sets the function which computes the default value of an attribute in case it is missing. The transformer
         * transforms from the requested value to the default value. It therefore allows reacting to several cases
         * based on the value which is requested. For example, for the attribute <code>flavor</code>, the value
         * <code>free</code> is requested. Then the transformer can determine if it should return <code>null</code>,
         * which means "do not provide a default value", or <code>free</code> to match exactly, or any other value
         * of interest.
         * @param defaultValueBuilder the value builder
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder setDefaultValue(Transformer<String, String> defaultValueBuilder);

        // The following methods are just handy DSL methods for common use cases

        /**
         * Sets the default value provider to always return the value which is requested. As a consequence,
         * the scorer should always match when an attribute is missing in a candidate configuration.
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder optional();

        /**
         * Sets the default value provider to always return <code>null</code>. As a consequence,
         * the scorer should never match when an attribute is missing in a candidate configuration.
         * This is the default behavior.
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder required();

        /**
         * Sets the scoring strategy to exact match. The required attribute value will be compared to the
         * value provided by the candidate configuration, and a match will be found if and only if the
         * required and provided attribute values are strictly equal. This is the default behavior.
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder matchStrictly();

        /**
         * Sets the default value to a constant, independent of the requested value. As a consequence, some
         * configuration attributes will match, some will not, depending on the scorer implementation. This
         * can be used to implement <i>reasonable fallback</i> values which do not match exactly but at some
         * distance.
         * @param value the constant value to use as the default value when no attribute is found
         * @return this builder
         */
        ConfigurationAttributeMatcherBuilder setDefaultValue(String value);
    }
}
