/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Incubating;

import java.io.File;
import java.util.List;

/**
 * The <a href="https://swift.org/">Swift Compiler</a> tool chain.
 *
 * @since 4.1
 */
@Incubating
public interface Swiftc extends NativeToolChain {
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
}
