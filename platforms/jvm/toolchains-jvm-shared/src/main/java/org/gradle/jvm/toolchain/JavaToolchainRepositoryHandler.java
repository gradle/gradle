/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.util.List;

/**
 * {@link org.gradle.api.NamedDomainObjectList} based handler for configuring an
 * ordered collection of <code>JavaToolchainRepository</code> implementations.
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainRepositoryHandler {

    /**
     * Utility method for creating a named {@link JavaToolchainRepository} based on
     * a configuration block.
     */
    void repository(String name, Action<? super JavaToolchainRepository> configureAction);

    /**
     * Returns a list of repositories that have been added so far. The list order
     * reflects the order in which the repositories have been declared.
     *
     * @since 7.6.1
     */
    List<JavaToolchainRepository> getAsList();

    /**
     * Returns the count of the repositories added so far.
     *
     * @since 7.6.1
     */
    int size();

    /**
     * Removes the repository with the given name.
     * <p>
     * Returns true if a repository with the specified name exists and has been successfully removed, false otherwise.
     *
     * @since 7.6.1
     */
    boolean remove(String name);

}
