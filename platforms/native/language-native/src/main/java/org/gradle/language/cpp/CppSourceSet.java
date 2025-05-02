/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.language.cpp;

import org.gradle.api.Incubating;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.nativeplatform.DependentSourceSet;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;

/**
 * A set of C++ source files.
 *
 * <p>A C++ source set contains a set of source files, together with an optional set of exported header files.</p>
 *
 * <pre class='autoTested'>
 * plugins {
 *     id 'cpp'
 * }
 *
 * model {
 *     components {
 *         main(NativeLibrarySpec) {
 *             sources {
 *                 cpp {
 *                     source {
 *                         srcDirs "src/main/cpp", "src/shared/c++"
 *                         include "**{@literal /}*.cpp"
 *                     }
 *                     exportedHeaders {
 *                         srcDirs "src/main/include", "src/shared/include"
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * </pre>
 */
@Incubating
public interface CppSourceSet extends HeaderExportingSourceSet, LanguageSourceSet, DependentSourceSet {
}
