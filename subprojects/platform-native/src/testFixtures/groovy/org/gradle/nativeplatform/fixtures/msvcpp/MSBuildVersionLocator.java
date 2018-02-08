/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.msvcpp;

import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VswhereVersionLocator;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;

public class MSBuildVersionLocator {
    private final VswhereVersionLocator vswhereLocator;

    public MSBuildVersionLocator(VswhereVersionLocator vswhereLocator) {
        this.vswhereLocator = vswhereLocator;
    }

    public File getMSBuildInstall() {
        File vswhere = vswhereLocator.getVswhereInstall();
        if (vswhere == null) {
            throw new IllegalStateException("vswhere tool is required to be installed");
        }

        TestFile installDir = new TestFile(new TestFile(vswhere).exec("-latest", "-products", "*", "-requires", "Microsoft.Component.MSBuild", "-property", "installationPath").getOut().trim());

        // TODO: Remove the hardcoded version, see https://github.com/Microsoft/vswhere/issues/74
        TestFile msbuild = installDir.file("MSBuild/15.0/Bin/MSBuild.exe");
        if (!msbuild.exists()) {
            throw new IllegalStateException("This test requires msbuild to be installed");
        }
        return msbuild;
    }
}
