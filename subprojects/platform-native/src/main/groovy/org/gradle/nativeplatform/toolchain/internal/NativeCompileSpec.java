/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.nativeplatform.internal.BinaryToolSpec;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A compile spec that will be used to generate object files for combining into a native binary.
 */
public interface NativeCompileSpec extends BinaryToolSpec {
    File getObjectFileDir();

    void setObjectFileDir(File objectFileDir);

    List<File> getIncludeRoots();

    void include(Iterable<File> includeRoots);

    void include(File... includeRoots);

    List<File> getSourceFiles();

    void setSourceFiles(Collection<File> sources);

    void source(Iterable<File> sources);

    List<File> getRemovedSourceFiles();

    void setRemovedSourceFiles(Collection<File> sources);

    void removedSource(Iterable<File> sources);

    Map<String, String> getMacros();

    void setMacros(Map<String, String> macros);

    void define(String name);

    void define(String name, String value);

    boolean isPositionIndependentCode();

    void setPositionIndependentCode(boolean flag);

    boolean isIncrementalCompile();

    void setIncrementalCompile(boolean flag);
}
