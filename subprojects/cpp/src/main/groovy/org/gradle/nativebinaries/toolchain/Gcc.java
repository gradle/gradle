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

import org.gradle.api.Incubating;

import java.io.File;
import java.util.List;

/**
 * The <a href="http://gcc.gnu.org/">GNU GCC</a> tool chain.
 */
@Incubating
public interface Gcc extends PlatformConfigurableToolChain {
    /**
     * The path required for executing the tool chain.
     * These are used to locate tools for this tool chain, and are prepended to the system PATH when executing these tools.
     */
    List<File> getPath();

    /**
     * Append an entry or entries to the tool chain path.
     *
     * @param pathEntries The path values to append. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     */
    void path(Object... pathEntries);
}
