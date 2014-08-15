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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;

import java.io.File;

/**
 * A shared library binary built by Gradle for a native library.
 */
@Incubating
public interface SharedLibraryBinarySpec extends NativeLibraryBinarySpec {
    /**
     * The shared library file.
     */
    File getSharedLibraryFile();

    /**
     * The shared library link file.
     */
    File getSharedLibraryLinkFile();

    /**
     * The shared library file.
     */
    void setSharedLibraryFile(File sharedLibraryFile);

    /**
     * The shared library link file.
     */
    void setSharedLibraryLinkFile(File sharedLibraryLinkFile);
}
