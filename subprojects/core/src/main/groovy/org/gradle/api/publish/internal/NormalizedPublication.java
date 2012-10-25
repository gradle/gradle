/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.internal;

/**
 * A normalized publication is a description of a publication in the raw language of a format.
 *
 * For example, a Publication may describe a complex component that has “api” and “impl” facets. If this
 * is to be published in the Ivy format, it needs to be expressed in terms of Ivy primitives (not “api” and “impl”).
 * When publishing to an Ivy repository, publishing occurs in terms of these primitives.
 *
 * There may be many types of ivy publications, for different kinds of components. These publications
 * can all describe themselves in a normalized, flattened, way that is the base terms of the format.
 * This allows the publishing mechanics to be ignorant of the different publication component types.
 *
 * This interface defines no contract because it is entirely format (e.g. Ivy, Maven etc.) specific.
 */
public interface NormalizedPublication {

}
