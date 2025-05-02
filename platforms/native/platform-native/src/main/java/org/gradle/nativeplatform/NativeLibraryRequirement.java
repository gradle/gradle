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
package org.gradle.nativeplatform;

import org.gradle.api.Incubating;

/**
 * A dependency on a native library within the build.
 */
@Incubating
public interface NativeLibraryRequirement {
    /**
     * The path to the project containing the library.
     */
    String getProjectPath();

    /**
     * The name of the required library.
     */
    String getLibraryName();

    /**
     * The required linkage.
     */
    String getLinkage();

    /**
     * Creates a copy of this requirement with the specified project path
     */
    NativeLibraryRequirement withProjectPath(String projectPath);
}
