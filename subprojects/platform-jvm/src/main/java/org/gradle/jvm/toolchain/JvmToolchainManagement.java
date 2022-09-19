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
 * the ordered list of <code>JavaToolchainRepository</code> implementations to use
 * for Java toolchain provisioning.
 *
 * @since 7.6
 */
@Incubating
public interface JvmToolchainManagement {

    // TODO (#21082): update user manual

    /**
     * Returns the handler for the ordered list of configured <code>JavaToolchainRepository</code> implementations.
     */
    JavaToolchainRepositoryResolverHandler getResolvers();

    /**
     * {@link org.gradle.api.NamedDomainObjectList} based handler for configuring an
     * ordered collection of <code>JavaToolchainRepository</code> implementations:
     *
     * <pre class='autoTested'>
     * toolchainManagement {
     *     jvm {
     *         resolvers {
     *             resolver('registry1') {
     *                 implementationClass = CustomToolchainRegistry1    // TODO (#21082): in a groovy script does a fully qualified class name go here?
     *                 credentials {
     *                     username "user"
     *                     password "password"
     *                 }
     *                 authentication {
     *                     digest(BasicAuthentication)
     *                 }
     *             }
     *             resolver('registry2') {
     *                 implementationClass = CustomToolchainRegistry2
     *             }
     *         }
     *     }
     * </pre>
     */
    void resolvers(Action<? super JavaToolchainRepositoryResolverHandler> configureAction);

}
