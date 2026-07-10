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
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.provider.Property;

/**
 * Named configuration of {@link JavaToolchainResolver} implementations,
 * identified by their implementation class.
 * <p>
 * The implementation class is the only mandatory property (it identifies
 * the {@link JavaToolchainResolver} being configured; the name is arbitrary,
 * chosen by the build author).
 * <p>
 * Authentication related configuration is optional.
 *
 * @since 7.6
 */
@Incubating
public interface JavaToolchainRepository extends AuthenticationSupported {

    /**
     * Name of the configuration, set by the build author, can be anything, as long
     * as it doesn't conflict with other repository names.
     */
    String getName();

    /**
     * Class implementing the {@link JavaToolchainResolver} being configured.
     * Mandatory property.
     */
    Property<Class<? extends JavaToolchainResolver>> getResolverClass();

}
