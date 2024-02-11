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

/**
 * The Visual C++ tool chain.
 */
@Incubating
public interface VisualCpp extends NativeToolChain {
    /**
     * The directory where Visual Studio or Visual C++ is installed.
     */
    File getInstallDir();

    /**
     * The directory where Visual Studio or Visual C++ is installed.
     */
    void setInstallDir(Object installDir);

    /**
     * The directory where Windows SDK is installed.
     */
    File getWindowsSdkDir();

    /**
     * The directory where Windows SDK is installed.
     */
    void setWindowsSdkDir(Object installDir);

    /**
     * Adds an action that can fine-tune the tool configuration for each platform supported by this tool chain.
     */
    void eachPlatform(Action<? super VisualCppPlatformToolChain> action);
}
