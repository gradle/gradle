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

package org.gradle.api;

/**
 * An attributes schema stores information about {@link Attribute attributes} and how they
 * can be matched together.
 */
@Incubating
public interface AttributesSchema {

    /**
     * Sets the attribute matching strategy of an attribute.
     * @param attribute the attribute to set the strategy for
     * @param strategy the strategy to assiociate with this attribute
     * @param <T> the type of the attribute
     */
    <T> void setMatchingStrategy(Attribute<T> attribute, AttributeMatchingStrategy<T> strategy);

    /**
     * Returns the matching strategy for a given attribute.
     * @param attribute the attribute
     * @param <T> the type of the attribute
     * @return the matching strategy for this attribute.
     */
    <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute);

    /**
     * Configures the matching strategy for the provided attribute with a standard equality
     * matching strategy: attribute values will be considered compatible if and only if they
     * match using the {@link Object#equals(Object) equals method}.
     *
     * @param attribute the attribute for which to set the strategy
     * @param <T> the type of the attribute
     */
    <T> void matchStrictly(Attribute<T> attribute);

}
