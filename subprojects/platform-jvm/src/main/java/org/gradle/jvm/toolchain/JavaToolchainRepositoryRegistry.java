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

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * The build level object/service provided by Gradle which Java Toolchain Provisioning SPI plugins can access
 * and register their <code>JavaToolchainRepository</code> implementations/build services into.
 *
 * @since 7.6
 */
@Incubating
@HasInternalProtocol
public interface JavaToolchainRepositoryRegistry {

    /**
     * Registers a <code>JavaToolchainRepository</code> implementation with a specific name. Fails if
     * any other repository has been already registered with that name (throws a <code>GradleException</code>).
     * <p>
     * To avoid name collisions, we recommend repository names be namespaced, like plugin-ids. For example,
     * if the plugin providing the repository has the plugin-id "com.domain.myrepo", then we recommend the
     * repository to be named "com_domain_myrepo". This way we can ensure that any combination of
     * Java Toolchain Provisioning SPI plugins will work in any build.
     * <p>
     * Dots and dashes need to be avoided in the repository names because at a later stage we will be generating
     * accessors for the repositories and that mechanism will not be able to handle them. In general, names
     * must start with a lowercase letter and contain only letters, numbers, and underscore characters.
     */
    <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType);

    //TODO (#21082): switch to NamedDomainObjectContainer based API proposal
    //TODO (#21082): update design docs
}
