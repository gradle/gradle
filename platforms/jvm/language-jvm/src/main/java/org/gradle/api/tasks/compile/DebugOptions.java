/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.annotation.Nullable;

/**
 * Debug options for Java compilation. Only take effect if {@link CompileOptions#debug}
 * is set to {@code true}.
 */
@SuppressWarnings("deprecation")
public class DebugOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private String debugLevel;

    /**
     * Get a comma-separated list of debug information to be generated during compilation.
     * The list may contain any of the following keywords (without spaces in between):
     *
     * <dl>
     *     <dt>{@code source}
     *     <dd>Source file debugging information
     *     <dt>{@code lines}
     *     <dd>Line number debugging information
     *     <dt>{@code vars}
     *     <dd>Local variable debugging information
     * </dl>
     *
     * <p>Alternatively, a value of {@code none} means debug information will not be generated.</p>
     *
     * <p>When the value is null, only source and line debugging information will be generated.</p>
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public String getDebugLevel() {
        return debugLevel;
    }

    /**
     * Sets which debug information is to be generated during compilation. The value must be a
     * comma-separated list containing any of the following keywords (without spaces in between):
     *
     * <dl>
     *     <dt>{@code source}
     *     <dd>Source file debugging information
     *     <dt>{@code lines}
     *     <dd>Line number debugging information
     *     <dt>{@code vars}
     *     <dd>Local variable debugging information
     * </dl>
     *
     * <p>For example {@code source,lines,vars} is a valid value.</p>
     *
     * <p>Alternatively setting the value to {@code none} will disable debug information generation.</p>
     *
     * <p>Setting this value to null will reset the property to its default value of only
     * generating line and source debug information.</p>
     */
    public void setDebugLevel(@Nullable String debugLevel) {
        this.debugLevel = debugLevel;
    }
}
