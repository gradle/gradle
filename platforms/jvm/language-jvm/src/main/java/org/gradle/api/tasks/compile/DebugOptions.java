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

import javax.annotation.Nullable;

/**
 * Debug options for Java compilation. Only take effect if {@link CompileOptions#debug}
 * is set to {@code true}.
 */
public class DebugOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private String debugLevel;

    /**
     * Tells which debugging information is to be generated. The value is a comma-separated
     * list of any of the following keywords (without spaces in between):
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
     * Also instead of those values {@code none} can be used.
     *
     * By default, only source and line debugging information will be generated.
     */
    @Nullable
    @Optional
    @Input
    public String getDebugLevel() {
        return debugLevel;
    }

    /**
     * Sets which debug information is to be generated. The value is a comma-separated
     * list of any of the following keywords (without spaces in between):
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
     * For example {@code source,lines,vars}. Alternatively {@code none} can be used
     * to completely disable debug information.
     */
    public void setDebugLevel(@Nullable String debugLevel) {
        this.debugLevel = debugLevel;
    }
}
