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
     * In order to avoid name collisions we recommend this name to be set to the plugin ID that's doing
     * the registration. Since plugin IDs are already forced to be unique by the Plugin Portal, this
     * will ensure that any combination of Java Toolchain Provisioning SPI plugins will work in any build.
     *
     * //TODO (#21082): dot and dash can't be used in the names
     *
     */
    <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType);

    //TODO (#21082): remove spec versioning from repository, move it to registration (param of the register method)

    //TODO (#21082): check this user provided name for the conventions we require

}
