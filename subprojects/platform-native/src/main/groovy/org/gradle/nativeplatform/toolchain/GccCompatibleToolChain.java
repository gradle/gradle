/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.toolchain;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.io.File;
import java.util.List;

/**
 * A ToolChain that can handle additional platforms simply by configuring the NativeBinary.
 */
@Incubating
public interface GccCompatibleToolChain extends NativeToolChain {
    /**
     * The paths setting required for executing the tool chain.
     * These are used to locate tools for this tool chain, and are prepended to the system PATH when executing these tools.
     */
    List<File> getPath();

    /**
     * Append an entry or entries to the tool chain path.
     *
     * @param pathEntries The path values to append. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     */
    void path(Object... pathEntries);

    /**
     * Add support for target platform specified by name.
     */
    public void target(String platformName);

    /**
     * Add configuration for a target platform specified by name with additional configuration action.
     */
    public void target(String platformName, Action<? super GccPlatformToolChain> action);

    /**
     * Adds an action that can fine-tune the tool configuration for each platform supported by this tool chain.
     */
    public void eachPlatform(Action<? super GccPlatformToolChain> action);
}
