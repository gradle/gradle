/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.provider.Provider;

/**
 * Allows to query for toolchain managed tools, like {@link JavaCompiler}, {@link JavaLauncher} and {@link JavadocTool}.
 * <p>
 * An instance of this service is available for injection into tasks, plugins and other types.
 *
 * @since 6.7
 */
public interface JavaToolchainService {

    /**
     * Obtain a {@link JavaCompiler} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaCompiler>}
     */
    Provider<JavaCompiler> compilerFor(Action<? super JavaToolchainSpec> config);

    /**
     * Obtain a {@link JavaCompiler} matching the {@link JavaToolchainSpec}.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaCompiler>}
     */
    Provider<JavaCompiler> compilerFor(JavaToolchainSpec spec);

    /**
     * Obtain a {@link JavaLauncher} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaLauncher>}
     */
    Provider<JavaLauncher> launcherFor(Action<? super JavaToolchainSpec> config);

    /**
     * Obtain a {@link JavaLauncher} matching the {@link JavaToolchainSpec}.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavaLauncher>}
     */
    Provider<JavaLauncher> launcherFor(JavaToolchainSpec spec);

    /**
     * Obtain a {@link JavadocTool} matching the {@link JavaToolchainSpec}, as configured by the provided action.
     *
     * @param config The configuration of the {@code JavaToolchainSpec}
     * @return A {@code Provider<JavadocTool>}
     */
    Provider<JavadocTool> javadocToolFor(Action<? super JavaToolchainSpec> config);

    /**
     * Obtain a {@link JavadocTool} matching the {@link JavaToolchainSpec}.
     *
     * @param spec The {@code JavaToolchainSpec}
     * @return A {@code Provider<JavadocTool>}
     */
    Provider<JavadocTool> javadocToolFor(JavaToolchainSpec spec);
}
