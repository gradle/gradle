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

public enum SymbolExtractorOsConfig {
    OBJCOPY("objcopy", Lists.newArrayList("--only-keep-debug"), ".debug"),
    DSYMUTIL("dsymutil", Lists.<String>newArrayList("-f"), ".dwarf") {
        @Override
        public List<String> getInputOutputFileArguments(String inputFilePath, String outputFilePath) {
            return Lists.newArrayList("-o", outputFilePath, inputFilePath);
        }
    };

    private static final OperatingSystem OS = OperatingSystem.current();

    private final String executable;
    private final List<String> arguments;
    private final String extension;

    SymbolExtractorOsConfig(String executable, List<String> arguments, String extension) {
        this.executable = executable;
        this.arguments = arguments;
        this.extension = extension;
    }

    public static SymbolExtractorOsConfig current() {
        if (OS.isMacOsX()) {
            return DSYMUTIL;
        } else {
            return OBJCOPY;
        }
    }

    public String getExecutableName() {
        return executable;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public List<String> getInputOutputFileArguments(String inputFilePath, String outputFilePath) {
        return Lists.newArrayList(inputFilePath, outputFilePath);
    }

    public String getExtension() {
        return extension;
    }
}
