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
 * The build level object/service provided by Gradle which Java toolchain provisioning plugins can access
 * to register their <code>JavaToolchainResolver</code> implementations/build services into.
 *
 * @since 7.6
 */
@Incubating
@HasInternalProtocol
public interface JavaToolchainResolverRegistry {

    /**
     * Registers a <code>JavaToolchainResolver</code> implementation. The class name should be properly
     * name-spaced, to avoid collisions (if another resolver class with the same fully qualified name
     * is registered, a <code>GradleException</code> will be thrown).
     */
    <T extends JavaToolchainResolver> void register(Class<T> implementationType);

}
