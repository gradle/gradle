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
import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;

import java.util.List;

/**
 * Dynamic extension added to <code>ToolchainManagementSpec</code> at runtime, by the
 * <code>jdk-toolchains</code> plugin. Provides a <code>jdks</code> block with methods
 * to specify which <code>JavaToolchainRepository</code> implementations to use
 * and in what order for Java toolchain provisioning.
 *
 * @since 7.6
 */
@Incubating
public interface JdksBlockForToolchainManagement {

    /**
     * Look up a registration by name and, if found, add it to the ordered list of explicitly requested registrations.
     * If not found, throw a <code>GradleException</code>.
     */
    void add(String registrationName);

    /**
     * Look up a registration by name and, if found, add it to the ordered list of explicitly requested registrations.
     * If not found, throw a <code>GradleException</code>.
     * <p>
     * Configures the authentication to be used when the requested registration ends up being used for Java
     * toolchain provisioning. The authentication mechanism employed are the same as the ones for artifact
     * repositories, and they need to be configured in exactly the same way:
     *
     * <pre class='autoTested'>
     * toolchainManagement {
     *   jdks {
     *     add("customRegistry") {
     *       credentials {
     *         username "user"
     *         password "password"
     *       }
     *       authentication {
     *         digest(DigestAuthentication)
     *       }
     *     }
     *   }
     * }
     * </pre>
     */
    void add(String registrationName, Action<? super JavaToolchainRepositoryRequestConfiguration> authenticationSupported);

    List<? extends JavaToolchainRepositoryRegistration> getAll();

}
