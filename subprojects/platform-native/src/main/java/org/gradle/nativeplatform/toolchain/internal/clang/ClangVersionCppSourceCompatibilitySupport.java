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

package org.gradle.nativeplatform.toolchain.internal.clang;

import org.gradle.nativeplatform.CppSourceCompatibility;
import org.gradle.util.VersionNumber;

public class ClangVersionCppSourceCompatibilitySupport {
    private static final String STD_CPP_2A = "-std=c++2a";
    private static final String STD_CPP_GNU_2A = "-std=gnu++2a";
    private static final String STD_CPP_17 = "-std=c++17";
    private static final String STD_CPP_GNU_17 = "-std=gnu++17";
    private static final String STD_CPP_1Z = "-std=c++1z";
    private static final String STD_CPP_GNU_1Z = "-std=gnu++1z";
    private static final String STD_CPP_14 = "-std=c++14";
    private static final String STD_CPP_GNU_14 = "-std=gnu++14";
    private static final String STD_CPP_1Y = "-std=c++1y";
    private static final String STD_CPP_GNU_1Y = "-std=gnu++1y";
    private static final String STD_CPP_11 = "-std=c++11";
    private static final String STD_CPP_GNU_11 = "-std=gnu++11";
    private static final String STD_CPP_03 = "-std=c++03";
    private static final String STD_CPP_GNU_03 = "-std=gnu++03";
    private static final String STD_CPP_98 = "-std=c++98";
    private static final String STD_CPP_GNU_98 = "-std=gnu++98";

    /**
     * Returns the default source compatibility for provided Clang version number.
     *
     * <p>By default, Clang builds C++ code according to the C++98 standard, so
     * {@link CppSourceCompatibility#Cpp98} is always returned</p>
     *
     * @return Default Clang source compatibility.
     */
    public static CppSourceCompatibility getDefaultSourceCompatibility() {
        return CppSourceCompatibility.Cpp98;
    }

    /**
     * Returns the language standard command-line option for Clang based on the version of Clang
     * being used and the requested language standard.
     *
     * @param version Clang version.
     * @param compat C++ source compatibility.
     * @return Clang language standard option.
     * @throws IllegalArgumentException If {@code compat} is not supported by the Clang version.
     */
    public static String getSourceCompatibilityOption(VersionNumber version, CppSourceCompatibility compat) {
        switch (compat) {
            case Cpp2a:
            case Cpp2aExtended:
                if (version.getMajor() >= 5) {
                    return compat == CppSourceCompatibility.Cpp2a ? STD_CPP_2A : STD_CPP_GNU_2A;
                }
                // toolchain does not support C++2a
                break;
            case Cpp17:
            case Cpp17Extended:
                if (version.getMajor() >= 5) {
                    return compat == CppSourceCompatibility.Cpp17 ? STD_CPP_17 : STD_CPP_GNU_17;
                } else if (version.getMajor() >= 4 || (version.getMajor() == 3 && version.getMinor() >= 5)) {
                    return compat == CppSourceCompatibility.Cpp17 ? STD_CPP_1Z : STD_CPP_GNU_1Z;
                }
                // toolchain does not support C++17
                break;
            case Cpp14:
            case Cpp14Extended:
                if (version.getMajor() >= 4 || (version.getMajor() == 3 && version.getMinor() >= 5)) {
                    return compat == CppSourceCompatibility.Cpp14 ? STD_CPP_14 : STD_CPP_GNU_14;
                } else if (version.getMajor() == 3 && version.getMinor() >= 2) {
                    return compat == CppSourceCompatibility.Cpp14 ? STD_CPP_1Y : STD_CPP_GNU_1Y;
                }
                // toolchain does not support C++14
                break;
            case Cpp11:
            case Cpp11Extended:
                if (version.getMajor() >= 3) {
                    return compat == CppSourceCompatibility.Cpp11 ? STD_CPP_11 : STD_CPP_GNU_11;
                }
                // toolchain does not support C++11
                break;
            case Cpp03:
            case Cpp03Extended:
                if (version.getMajor() >= 5) {
                    return compat == CppSourceCompatibility.Cpp03 ? STD_CPP_03 : STD_CPP_GNU_03;
                } else if (version.getMajor() >= 3) {
                    return STD_CPP_03;
                }
                // toolchain does not support C++03
                break;
            case Cpp98Extended:
                return STD_CPP_GNU_98;
            case Cpp98:
                // Fall through
            default:
                return STD_CPP_98;
        }
        throw new IllegalArgumentException(String.format("clang %s does not support %s", version, compat));
    }
}
