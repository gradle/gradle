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

package org.gradle.nativeplatform.toolchain.internal.compilespec;

import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;

import java.io.File;
import java.util.Collection;

public interface SwiftCompileSpec extends NativeCompileSpec {
    String getModuleName();
    void setModuleName(String moduleName);

    File getModuleFile();
    void setModuleFile(File file);

    SwiftVersion getSourceCompatibility();
    void setSourceCompatibility(SwiftVersion sourceCompatibility);

    Collection<File> getChangedFiles();
    void setChangedFiles(Collection<File> changedFiles);
}
