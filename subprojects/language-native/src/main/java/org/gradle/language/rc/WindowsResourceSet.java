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
package org.gradle.language.rc;

import org.gradle.api.Incubating;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.language.nativeplatform.NativeResourceSet;
import org.gradle.language.base.LanguageSourceSet;

/**
 * A set of Windows Resource definition files.
 *
 * <p>A Windows Resource set contains a set of script files, together with an optional set of header files.</p>
 *
 * <pre class='autoTested'>
 * apply plugin: "windows-resources"
 *
 * model {
 *     components {
 *         main(NativeLibrarySpec) {
 *             sources {
 *                 rc {
 *                     source {
 *                         srcDirs "src/main/rc"
 *                         include "**{@literal /}*.rc"
 *                     }
 *                     exportedHeaders {
 *                         srcDirs "src/main/include"
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * </pre>
 */
@Incubating
public interface WindowsResourceSet extends LanguageSourceSet, HeaderExportingSourceSet, NativeResourceSet {
}
