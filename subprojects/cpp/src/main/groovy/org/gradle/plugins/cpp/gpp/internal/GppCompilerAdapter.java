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
package org.gradle.plugins.cpp.gpp.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.plugins.binaries.model.Binary;
import org.gradle.plugins.binaries.model.internal.CompileSpecFactory;
import org.gradle.plugins.binaries.model.Library;
import org.gradle.plugins.binaries.model.internal.CompilerAdapter;
import org.gradle.plugins.cpp.gpp.GppCompileSpec;
import org.gradle.plugins.cpp.gpp.GppLibraryCompileSpec;

/**
 * Compiler adapter for g++
 */
public class GppCompilerAdapter implements CompilerAdapter<GppCompileSpec> {

    public static final String NAME = "gpp";
    private final GppCompiler compiler;
    private final ProjectInternal project;

    public GppCompilerAdapter(ProjectInternal project) {
        this.project = project;
        compiler = new GppCompiler(project.getFileResolver());
    }

    public String getName() {
        return NAME;
    }

    public CompileSpecFactory<GppCompileSpec> getSpecFactory() {
        return new CompileSpecFactory<GppCompileSpec>() {
            public GppCompileSpec create(Binary binary) {
                if (binary instanceof Library) {
                    return new GppLibraryCompileSpec(binary, compiler, project);
                }
                return new GppCompileSpec(binary, compiler, project);
            }
        };
    }
}