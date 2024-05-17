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

package org.gradle.internal.resource.local;

/**
 * Can find a locally available candidates for an external resource, through some means.
 *
 * This is different to our caching in that we know very little about locally available resources, other than their
 * binary content. If we can determine the sha1 value of an external resource, we can search the local system to see
 * if a copy can be found (e.g. the local Maven cache).
 *
 * @param <C> The type of the criterion object used to find candidates
 */
public interface LocallyAvailableResourceFinder<C> {

    LocallyAvailableResourceCandidates findCandidates(C criterion);

}
