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

package org.gradle.ide.xcode.internal.xcodeproj;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

/**
 * File types used in Apple targets.
 */
public enum FileTypes {
    /**
     * Apple UTI for executables.
     */
    MACH_O_EXECUTABLE("", "compiled.mach-o.executable"),

    /**
     * Apple UTI for dynamic libraries.
     */
    MACH_O_DYNAMIC_LIBRARY("dylib", "compiled.mach-o.dylib"),

    /**
     * Apple UTI for static libraries.
     */
    ARCHIVE_LIBRARY("a", "archive.ar"),

    C_SOURCE_CODE("c", "sourcecode.c.c"),
    CC_SOURCE_CODE("cc", "sourcecode.cpp.cpp"),
    CPP_SOURCE_CODE("cpp", "sourcecode.cpp.cpp"),
    CXX_SOURCE_CODE("cxx", "sourcecode.cpp.cpp"),
    H_SOURCE_CODE("h", "sourcecode.c.h"),
    SWIFT_SOURCE_CODE("swift", "sourcecode.swift"),
    XCODE_PROJECT_WRAPPER("xcodeproj", "wrapper.pb-project");

    public final String fileExtension;
    public final String identifier;
    FileTypes(String fileExtension, String identifier) {
        this.fileExtension = fileExtension;
        this.identifier = identifier;
    }


    /**
     * Map of file extension to Apple UTI (Uniform Type Identifier).
     */
    public static final ImmutableMap<String, String> FILE_EXTENSION_TO_UTI;

    static {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (FileTypes fileType : FileTypes.values()) {
            builder.put(fileType.fileExtension, fileType.identifier);
        }

        FILE_EXTENSION_TO_UTI = builder.build();
    }

    /**
     * Multimap of Apple UTI (Uniform Type Identifier) to file extension(s).
     */
    public static final ImmutableMultimap<String, String> UTI_TO_FILE_EXTENSIONS;

    static {
        // Invert the map of (file extension -> UTI) pairs to
        // (UTI -> [file extension 1, ...]) pairs.
        ImmutableMultimap.Builder<String, String> builder = ImmutableMultimap.builder();
        for (ImmutableMap.Entry<String, String> entry : FILE_EXTENSION_TO_UTI.entrySet()) {
            builder.put(entry.getValue(), entry.getKey());
        }
        UTI_TO_FILE_EXTENSIONS = builder.build();
    }
}
