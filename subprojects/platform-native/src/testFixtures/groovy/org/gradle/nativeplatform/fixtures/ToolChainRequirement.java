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

package org.gradle.nativeplatform.fixtures;

public enum ToolChainRequirement {
    // Any available tool chain
    AVAILABLE,
    // Any available Visual Studio implementation
    VISUALCPP,
    // Any available Visual Studio >= 2012
    VISUALCPP_2012_OR_NEWER,
    // Exactly Visual Studio 2013
    VISUALCPP_2013,
    // Any available Visual Studio >= 2013
    VISUALCPP_2013_OR_NEWER,
    // Exactly Visual Studio 2015
    VISUALCPP_2015,
    // Any available Visual Studio >= 2015
    VISUALCPP_2015_OR_NEWER,
    // Any available GCC implementation (including mingw, cygwin, but not clang)
    GCC,
    // Any available GCC compatible implementation (including mingw, cygwin, and clang)
    GCC_COMPATIBLE,
    // Any available Clang
    CLANG,
    // Any Swift Compiler
    SWIFT
}
