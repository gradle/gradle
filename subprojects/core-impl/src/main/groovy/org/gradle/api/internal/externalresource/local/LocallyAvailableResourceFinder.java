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

package org.gradle.api.internal.externalresource.local;

/**
 * Provides a view into the local for finding artifacts that match a given criterion.
 *
 * It's intended to be used before searching/fetching from repositories using the given criteria in some way, which may be expensive.
 * Because of this, you ask the cache for candidates if there are any and only then use the criterion in the search.
 *
 * @param <C>
 */
public interface LocallyAvailableResourceFinder<C> {

    LocallyAvailableResourceCandidates findCandidates(C criterion);

}
