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

package org.gradle.api.toolchain.management;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * Object created when a Java Toolchain Provisioning SPI plugin registers a <code>JavaToolchainRepository</code>
 * with the <code>JavaToolchainRepositoryRegistry</code> service.
 * <p>
 * Can also be used to interact with the <code>toolchainManagement.jdks</code> block provided by the
 * <code>jvm-toolchains</code> plugin. In fact the <code>jdks</code> block will have generated accessors
 * for these registrations.
 *
 * @since 7.6
 */
@Incubating
@HasInternalProtocol
public interface JavaToolchainRepositoryRegistration {

    String getName();

    String getType();

}
