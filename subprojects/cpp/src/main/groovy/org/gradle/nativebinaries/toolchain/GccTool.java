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

package org.gradle.nativebinaries.toolchain;

import groovy.lang.Closure;
import org.gradle.api.Incubating;

/**
 * An executable tool that forms part of a tool chain.
 */
@Incubating
public interface GccTool {
    /**
     * The name of the executable file for this tool.
     */
    String getExecutable();

    /**
     * Set the name of the executable file for this tool.
     * The executable will be located in the tool chain path.
     */
    void setExecutable(String file);

    /**
     * Adds an action that will be applied to the command-line arguments prior to execution.
     */
    void withArguments(Closure arguments);
}
