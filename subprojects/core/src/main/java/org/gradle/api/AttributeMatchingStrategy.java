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
 * An attribute matching strategy is responsible for providing information about how an {@link Attribute}
 * is matched during dependency resolution. In particular, it will tell if a value, provided by a consumer,
 * is compatible with a value provided by a candidate.
 * @param <T> the type of the attribute
 */
@Incubating
public interface AttributeMatchingStrategy<T> {
    /**
     * Tells if the candidate value is compatible with the requested value. Compatibility doesn't mean
     * equality. A candidate value may be different from the requested value, but still compatible.
     * For example, Java 6 classes are compatible with a Java 7 runtime.
     * @param requestedValue the requested value
     * @param candidateValue a candidate value
     * @return true if the candidate value is <i>compatible with</i> the requested value
     */
    boolean isCompatible(T requestedValue, T candidateValue);
}
