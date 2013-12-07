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

package org.gradle.nativebinaries.toolchain.internal.msvcpp;

import java.io.File;

import org.gradle.api.specs.Spec;
import org.gradle.internal.os.OperatingSystem;

public class DefaultVisualStudioLocator extends DefaultWindowsLocator implements VisualStudioLocator {
    private static final String COMPILER_PATH = "VC/bin/";
    private static final String COMPILER_FILENAME = "cl.exe";

    private static final VersionLookupPath[] VISUALSTUDIO_PATHS = {
        new VersionLookupPath("/Microsoft Visual Studio 12.0", VisualStudioVersion.VS_2013),
        new VersionLookupPath("/Microsoft Visual Studio 11.0", VisualStudioVersion.VS_2012),
        new VersionLookupPath("/Microsoft Visual Studio 10.0", VisualStudioVersion.VS_2010)
    };

    private final OperatingSystem os;

    public DefaultVisualStudioLocator() {
        this(OperatingSystem.current());
    }

    public DefaultVisualStudioLocator(OperatingSystem os) {
        this.os = os;
    }

    public Search locateVisualStudio(File candidate) {
        return locateInHierarchy(candidate, isVisualStudio());
    }

    public Search locateDefaultVisualStudio() {
        Spec<File> isVisualStudio = isVisualStudio();
        // If cl.exe is on the path, assume it is contained within a visual studio install
        File compilerInPath = os.findInPath(COMPILER_FILENAME);
        if (compilerInPath != null) {
            return locateInHierarchy(compilerInPath, isVisualStudio);
        }

        return locateInProgramFiles(isVisualStudio, VISUALSTUDIO_PATHS);
    }

    private Spec<File> isVisualStudio() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return new File(element, COMPILER_PATH + COMPILER_FILENAME).isFile();
            }
        };
    }

}
