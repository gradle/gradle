/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.platform.base.ToolChain;

/**
 * A set of tools for building from Java source.
 *
 * <p>A {@code JavaToolChain} is able to:
 *
 * <ul>
 *
 * <li>Compile Java source to bytecode.</li>
 *
 * <li>Generate Javadoc from Java source.</li>
 *
 * </ul>
 */
@HasInternalProtocol
@Deprecated
public interface JavaToolChain extends ToolChain {
    /**
     * The version of the toolchain.
     *
     * @since 3.5
     */
    @Input
    String getVersion();

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    String getDisplayName();

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    String getName();
}
