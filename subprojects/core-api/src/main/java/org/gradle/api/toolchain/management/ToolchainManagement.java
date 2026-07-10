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
import org.gradle.api.plugins.ExtensionAware;

/**
 * Configures how toolchains are resolved.
 * <p>
 * <code>ToolchainManagement</code> is extended by plugins to support different ecosystems.
 * <p>
 * One option available at present is using the <code>jvm-toolchain-management</code> plugin, which
 * adds a <code>jvm</code> block to it, allowing for the configuration of toolchains used in the
 * JVM ecosystem.
 *
 * @since 7.6
 */
@Incubating
public interface ToolchainManagement extends ExtensionAware {
}
