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
package org.gradle.ide.visualstudio.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.nativebinaries.NativeBinary;
import org.gradle.nativebinaries.internal.NativeBinaryInternal;

public class VisualStudioSolutionBuilder {
    private final FileResolver fileResolver;
    private final VisualStudioProjectResolver projectResolver;

    public VisualStudioSolutionBuilder(FileResolver fileResolver, VisualStudioProjectResolver projectResolver) {
        this.fileResolver = fileResolver;
        this.projectResolver = projectResolver;
    }

    public VisualStudioSolution createSolution(NativeBinary nativeBinary) {
        return new VisualStudioSolution(solutionName(nativeBinary), (NativeBinaryInternal) nativeBinary, fileResolver, projectResolver);
    }

    private String solutionName(NativeBinary nativeBinary) {
        return projectResolver.lookupProjectConfiguration(nativeBinary).getProject().getName();
    }
}
