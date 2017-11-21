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

package org.gradle.nativeplatform.toolchain.internal;

import com.google.common.collect.Lists;
import org.gradle.internal.os.OperatingSystem;

import java.util.List;

public class SymbolExtractorOsConfig {
    private static final OperatingSystem OS = OperatingSystem.current();

    public static String getExecutableName() {
        if (OS.isMacOsX()) {
            return "dsymutil";
        } else {
            return "objcopy";
        }
    }

    public static List<String> getArguments() {
        if (OS.isMacOsX()) {
            return Lists.newArrayList("-f");
        } else {
            return Lists.newArrayList("--only-keep-debug");
        }
    }

    public static List<String> getInputOutputFileArguments(String inputFilePath, String outputFilePath) {
        if (OS.isMacOsX()) {
            return Lists.newArrayList("-o", outputFilePath, inputFilePath);
        } else {
            return Lists.newArrayList(inputFilePath, outputFilePath);
        }
    }

    public static String getExtension() {
        if (OS.isMacOsX()) {
            return ".dwarf";
        } else {
            return ".debug";
        }
    }
}
