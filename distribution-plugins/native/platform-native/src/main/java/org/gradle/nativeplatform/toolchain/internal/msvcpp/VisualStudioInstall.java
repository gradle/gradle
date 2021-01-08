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

import com.google.common.base.Preconditions;
import org.gradle.api.Named;
import org.gradle.util.VersionNumber;

import java.io.File;

public class VisualStudioInstall implements Named {
    private final String name;
    private final VisualCppInstall visualCppInstall;
    private final File baseDir;
    private final VersionNumber version;

    public VisualStudioInstall(String name, File baseDir, VersionNumber version, VisualCppInstall visualCppInstall) {
        this.name = name;
        this.baseDir = baseDir;
        this.visualCppInstall = Preconditions.checkNotNull(visualCppInstall);
        this.version = version;
    }

    @Override
    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public File getVisualStudioDir() {
        return baseDir;
    }

    public VisualCppInstall getVisualCpp() {
        return visualCppInstall;
    }
}
