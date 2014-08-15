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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Named;
import org.gradle.util.VersionNumber;

import java.io.File;

public class VisualStudioInstall implements Named {
    private final VisualCppInstall visualCppInstall;
    private final File baseDir;

    public VisualStudioInstall(File baseDir,  VisualCppInstall visualCppInstall) {
        this.baseDir = baseDir;
        this.visualCppInstall = visualCppInstall;
    }

    public String getName() {
        return visualCppInstall.getName();
    }

    public VersionNumber getVersion() {
        return visualCppInstall.getVersion();
    }

    public File getVisualStudioDir() {
        return baseDir;
    }

    public VisualCppInstall getVisualCpp() {
        return visualCppInstall;
    }
}
