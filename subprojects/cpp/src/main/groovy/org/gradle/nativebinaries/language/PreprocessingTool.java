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

package org.gradle.nativebinaries.language;

import org.gradle.api.Incubating;
import org.gradle.nativebinaries.Tool;

import java.util.Map;

/**
 * A tool that permits configuration of the C preprocessor.
 */
@Incubating
public interface PreprocessingTool extends Tool {
    /**
     * The set of preprocessor macros to define when compiling this binary.
     */
    Map<String, String> getMacros();

    /**
     * Defines a named preprocessor macros to use when compiling this binary.
     * The macro will be supplied to the compiler as '-D name'.
     */
    void define(String name);

    /**
     * Defines a named preprocessor macro with a value, which will be used when compiling this binary.
     * The macro will be supplied to the compiler as '-D name=definition'.
     */
    void define(String name, String definition);
}
