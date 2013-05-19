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

package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.plugins.binaries.model.Library;
import org.gradle.plugins.binaries.model.NativeComponent;
import org.gradle.plugins.binaries.model.internal.BinaryCompileSpec;
import org.gradle.plugins.binaries.model.internal.BinaryCompileSpecFactory;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.gradle.plugins.cpp.gpp.GppLibraryCompileSpec;

public class GppCompileSpecFactory implements BinaryCompileSpecFactory {

    public BinaryCompileSpec create(NativeComponent binary, org.gradle.api.internal.tasks.compile.Compiler<?> compiler) {
        Compiler<? super GppCompileSpec> typed = (Compiler<? super GppCompileSpec>) compiler;
        if (binary instanceof Library) {
            return new GppLibraryCompileSpec(binary, typed);
        }
        return new GppCompileSpec(binary, typed);
    }
}
