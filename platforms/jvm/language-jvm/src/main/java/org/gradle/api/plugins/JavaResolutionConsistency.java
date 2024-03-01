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
package org.gradle.api.plugins;

import org.gradle.api.Incubating;

/**
 * Dependency resolution consistency configuration for
 * the Java derived plugins.
 *
 * @since 6.8
 */
@Incubating
public interface JavaResolutionConsistency {
    /**
     * Configures the runtime classpath of every source set to be consistent
     * with the compile classpath. For dependencies which are common between
     * the compile classpath and the runtime classpath, the version from the
     * compile classpath is going to be used.
     *
     * Unless you have a good reason to, this option should be preferred to
     * {@link #useRuntimeClasspathVersions()} for different reasons:
     *
     * <ul>
     *     <li>As code is compiled first against the given dependencies,
     *     it is expected that the versions at runtime would be the same.
     *     </li>
     *     <li>It avoids resolving the runtime classpath in case of a compile error.
     *     </li>
     * </ul>
     *
     * In addition, the test compile classpath is going to be configured to
     * be consistent with the main compile classpath.
     */
    void useCompileClasspathVersions();

    /**
     * Configures the compile classpath of every source set to be consistent
     * with the runtime classpath. For dependencies which are common between
     * the compile classpath and the runtime classpath, the version from the
     * runtime classpath is going to be used.
     *
     * In addition, the test runtime classpath is going to be configured to
     * be consistent with the main runtime classpath.
     *
     * Prefer {@link #useCompileClasspathVersions()} unless you have special
     * requirements at runtime.
     */
    void useRuntimeClasspathVersions();
}
