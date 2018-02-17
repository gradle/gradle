/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.List;

public interface NativeTools {
    /**
     * Returns the implementation version of these tools.
     */
    VersionNumber getVersion();

    File getCompilerExecutable();

    File getLinkerExecutable();

    File getArchiverExecutable();

    File getAssemblerExecutable();

    File getBinDir();

    /**
     * Returns the path entries that must be present in order to use these tools, possibly none.
     */
    List<File> getPath();
}
