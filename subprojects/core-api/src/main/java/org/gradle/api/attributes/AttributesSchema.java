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

package org.gradle.api.attributes;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.util.List;
import java.util.Set;

/**
 * An attributes schema stores information about {@link Attribute attributes} and how they
 * can be matched together.
 *
 * @since 3.3
 *
 */
public interface AttributesSchema {

    /**
     * Returns the matching strategy for a given attribute.
     * @param attribute the attribute
     * @param <T> the type of the attribute
     * @return the matching strategy for this attribute.
     * @throws IllegalArgumentException When no strategy is available for the given attribute.
     */
    <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) throws IllegalArgumentException;

    /**
     * Declares a new attribute in the schema and configures it with the default strategy.
     * If the attribute was already declared it will simply return the existing strategy.
     *
     * @param attribute the attribute to declare in the schema
     * @param <T> the concrete type of the attribute
     *
     * @return the matching strategy for this attribute
     */
    <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute);

    /**
     * Configures the matching strategy for an attribute. The first call to this method for a specific attribute
     * will create a new matching strategy, whereas subsequent calls will configure the existing one.
     *
     * @param attribute the attribute for which to configure the matching strategy
     * @param configureAction the strategy configuration
     * @param <T> the concrete type of the attribute
     * @return the configured strategy
     */
    <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, Action<? super AttributeMatchingStrategy<T>> configureAction);

    /**
     * Returns the set of attributes known to this schema.
     */
    Set<Attribute<?>> getAttributes();

    /**
     * Returns true when this schema contains the given attribute.
     */
    boolean hasAttribute(Attribute<?> key);

    /**
     * Adds attributes to the precedence order. Attributes listed first have higher precedence.
     * <p>
     * The attributes will be added to any existing precedence order. If an attribute has already been added, adding it again
     * will fail.
     *
     * @param attributes the attributes in order
     *
     * @since 7.5
     * @see #setAttributeDisambiguationPrecedence(List)
     */
    @Incubating
    void attributeDisambiguationPrecedence(Attribute<?>... attributes);

    /**
     * Sets the order of precedence of attributes when resolving ambiguity. Attributes listed first have higher precedence.
     * <p>
     * By default, there is no explicit precedence between attributes and all attributes must be considered when disambiguating.
     *
     * @param attributes the attributes in order, highest priority first
     * @since 7.5
     */
    @Incubating
    void setAttributeDisambiguationPrecedence(List<Attribute<?>> attributes);

    /**
     * Returns the order that attributes should be considered when resolving ambiguity.
     *
     * @return an <strong>immutable</strong> list of the attributes in precedence order, with the highest priority first
     * @since 7.5
     */
    @Incubating
    List<Attribute<?>> getAttributeDisambiguationPrecedence();

    /**
     * TODO: Document
     *
     * @since 8.4
     */
    @Incubating
    void addVariantMatchingFailureInterpreter(VariantMatchingFailureInterpreter interpreter);
}
