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

/**
 * Dynamic extension added to <code>ToolchainManagement</code> at runtime, by the
 * <code>jvm-toolchain-management</code> plugin. Provides a <code>jvm</code> block to configure
 * the ordered list of Java toolchain repositories to use for auto-provisioning.
 *
 * @since 7.6
 */
@Incubating
public interface JvmToolchainManagement {

    /**
     * Returns the handler for the ordered list of configured <code>JavaToolchainRepository</code> implementations.
     */
    JavaToolchainRepositoryHandler getJavaRepositories();

    /**
     * {@link org.gradle.api.NamedDomainObjectList} based handler for configuring an
     * ordered collection of Java toolchain repositories:
     *
     * <pre>
     * toolchainManagement {
     *     jvm {
     *         javaRepositories {
     *             repository('registry1') {
     *                 resolverClass = com.example.CustomToolchainRegistry1
     *                 credentials {
     *                     username = "user"
     *                     password = "password"
     *                 }
     *                 authentication {
     *                     digest(BasicAuthentication)
     *                 }
     *             }
     *             repository('registry2') {
     *                 resolverClass = com.example.CustomToolchainRegistry2
     *             }
     *         }
     *     }
     * </pre>
     */
    void javaRepositories(Action<? super JavaToolchainRepositoryHandler> configureAction);

}
