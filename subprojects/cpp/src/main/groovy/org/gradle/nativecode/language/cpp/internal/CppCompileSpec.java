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

package org.gradle.nativecode.language.cpp.internal;

import org.gradle.nativecode.base.internal.BinaryCompileSpec;

import java.io.File;

public interface CppCompileSpec extends BinaryCompileSpec {

    Iterable<File> getIncludeRoots();

    void setIncludeRoots(Iterable<File> includeRoots);

    Iterable<File> getSource();

    void setSource(Iterable<File> source);

    boolean isForDynamicLinking();

    void setForDynamicLinking(boolean flag);

    Iterable<String> getMacros();

    void setMacros(Iterable<String> macros);

    Iterable<String> getArgs();

    void setArgs(Iterable<String> args);
}
