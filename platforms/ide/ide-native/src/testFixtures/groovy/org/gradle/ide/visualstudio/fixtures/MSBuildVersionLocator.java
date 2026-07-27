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

package org.gradle.ide.visualstudio.fixtures;

import org.gradle.nativeplatform.fixtures.AvailableToolChains;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VswhereVersionLocator;
import org.gradle.test.fixtures.file.ExecOutput;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.internal.VersionNumber;

import java.io.File;

public class MSBuildVersionLocator {
    private final VswhereVersionLocator vswhereLocator;

    public MSBuildVersionLocator(VswhereVersionLocator vswhereLocator) {
        this.vswhereLocator = vswhereLocator;
    }

    public File getMSBuildInstall(AvailableToolChains.InstalledToolChain toolChain) {
        VersionNumber vsVersion;
        if (toolChain instanceof AvailableToolChains.InstalledVisualCpp) {
            AvailableToolChains.InstalledVisualCpp visualCpp = (AvailableToolChains.InstalledVisualCpp) toolChain;
            vsVersion = visualCpp.getVersion();
        } else {
            vsVersion = VersionNumber.version(15);
        }

        File vswhere = vswhereLocator.getVswhereInstall();
        if (vswhere == null) {
            throw new IllegalStateException("vswhere tool is required to be installed");
        }

        ExecOutput vsWhereOutput = new TestFile(vswhere).exec("-version", String.format("[%s.0,%s.0)", vsVersion.getMajor(), vsVersion.getMajor() + 1), "-products", "*", "-requires", "Microsoft.Component.MSBuild", "-property", "installationPath");
        if (!vsWhereOutput.getError().trim().isEmpty()) {
            throw new IllegalStateException(String.format("Could not determine the location of MSBuild %s: %s", vsVersion.getMajor(), vsWhereOutput.getError()));
        }

        String location = vsWhereOutput.getOut().trim();
        TestFile msbuild;
        if (!location.isEmpty()) {
            msbuild = new TestFile(location).file("MSBuild/" + vsVersion.getMajor() + ".0/Bin/MSBuild.exe");
        } else if (vsVersion.getMajor() == 11) {
            msbuild = new TestFile("C:/Windows/Microsoft.Net/Framework/v4.0.30319/MSBuild.exe");
        } else {
            msbuild = new TestFile("C:/program files (x86)/MSBuild/" + vsVersion.getMajor() + ".0/Bin/MSBuild.exe");
        }

        // There is some value to the other ways to locate MSBuild (aka matching the MSBuild installation with the VS installation), this is a last chance to try and locate a usable MSBuild installation which will just try to get the latest available MSBuild. We can refine this later.
        if (!msbuild.exists()) {
            vsWhereOutput = new TestFile(vswhere).exec("-latest", "-requires", "Microsoft.Component.MSBuild", "-find", "MSBuild\\**\\Bin\\MSBuild.exe");
            location = vsWhereOutput.getOut().trim();
            if (location.isEmpty()) {
                throw new IllegalStateException(String.format("Could not determine the location of MSBuild %s: %s", vsVersion.getMajor(), vsWhereOutput.getError()));
            }

            msbuild = new TestFile(location);
        }

        if (!msbuild.exists()) {
            throw new IllegalStateException(String.format("This test requires MSBuild %s to be installed. Expected it to be installed at %s.", vsVersion.getMajor(), msbuild));
        }
        return msbuild;
    }
}
