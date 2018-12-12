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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;

/**
 * Configures the C++ source compatibility that is to be used when compiling the C++ source files.
 *
 * @since 5.1
 */
@Incubating
public enum CppSourceCompatibility {
    /**
     * Configures the source to be compatible with the 1998 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=c++98} command-line flag for GCC.</p>
     */
    Cpp98,

    /**
     * Configures the source to be compatible with the compiler-specific extensions of the 1998 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=gnu++98} command-line flag for GCC.</p>
     */
    Cpp98Extended,

    /**
     * Configures the source to be compatible with the 1998 ISO C++ standard plus the 2003 technical corrigendum.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=c++03} command-line flag for GCC.</p>
     */
    Cpp03,

    /**
     * Configures the source to be compatible with the compiler-specific extensions of the 1998 ISO C++
     * standard plus the 2003 technical corrigendum.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=gnu++03} command-line flag for GCC.</p>
     */
    Cpp03Extended,

    /**
     * Configures the source to be compatible with the 2011 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=c++11} command-line flag for GCC.</p>
     */
    Cpp11,

    /**
     * Configures the source to be compatible with the compiler-specific extensions of the 2011 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=gnu++11} command-line flag for GCC.</p>
     */
    Cpp11Extended,

    /**
     * Configures the source to be compatible with the 2014 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=c++14} command-line flag for GCC.</p>
     */
    Cpp14,

    /**
     * Configures the source to be compatible with the compiler-specific extensions of the 2014 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=gnu++14} command-line flag for GCC.</p>
     */
    Cpp14Extended,

    /**
     * Configures the source to be compatible with the 2017 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=c++17} command-line flag for GCC.</p>
     */
    Cpp17,

    /**
     * Configures the source to be compatible with the compiler-specific extensions of the 2017 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=gnu++17} command-line flag for GCC.</p>
     */
    Cpp17Extended,

    /**
     * Configures the source to be compatible with the expected 2020 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=c++2a} command-line flag for GCC.</p>
     */
    Cpp2a,

    /**
     * Configures the source to be compatible with the compiler-specific extensions of the expected 2020 C++ standard.
     *
     * <p>For example, this is equivalent to specifying the {@code -std=gnu++2a} command-line flag for GCC.</p>
     */
    Cpp2aExtended,
}
