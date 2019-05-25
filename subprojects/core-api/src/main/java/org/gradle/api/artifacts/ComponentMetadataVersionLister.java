/*
 * Copyright 2018 the original author or authors.
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

/**
 * Interface for custom version listers. A version lister is responsible for
 * returning the list of versions of a module which are available in a specific
 * repository. For this, Gradle is going to call the lister once for each module
 * it needs the list of versions. This will typically happen in case a dynamic
 * version is requested, in which case we need to know the list of versions
 * published for this module. It will not, however, be called for fixed version
 * numbers.
 *
 * @since 4.9
 */
@Incubating
public interface ComponentMetadataVersionLister extends Action<ComponentMetadataListerDetails> {
    /**
     * Perform a version listing query
     * @param details the details of the version listing query
     *
     * @since 4.9
     */
    @Override
    void execute(ComponentMetadataListerDetails details);
}
