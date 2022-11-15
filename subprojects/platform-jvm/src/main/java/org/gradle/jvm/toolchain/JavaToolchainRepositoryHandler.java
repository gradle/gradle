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
import org.gradle.api.NamedDomainObjectList;

/**
 * {@link org.gradle.api.NamedDomainObjectList} based handler for configuring an
 * ordered collection of <code>JavaToolchainRepository</code> implementations.
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainRepositoryHandler extends NamedDomainObjectList<JavaToolchainRepository> {

    /**
     * Utility method for creating a named {@link JavaToolchainRepository} based on
     * a configuration block.
     */
    void repository(String name, Action<? super JavaToolchainRepository> configureAction);

}
