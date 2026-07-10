/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.internal.xcodeproj;

/**
 * Concrete target type representing targets built by xcode itself, rather than an external build
 * system.
 */
public class PBXLegacyTarget extends PBXTarget {
    private String buildArgumentsString = "$(ACTION)";
    private String buildToolPath = "/usr/bin/make";
    private String buildWorkingDirectory;
    private boolean passBuildSettingsInEnvironment = true;

    public PBXLegacyTarget(String name, ProductType productType) {
        super(name, productType);
    }

    @Override
    public String isa() {
        return "PBXLegacyTarget";
    }

    public String getBuildArgumentsString() {
        return buildArgumentsString;
    }

    public void setBuildArgumentsString(String buildArgumentsString) {
        this.buildArgumentsString = buildArgumentsString;
    }

    public String getBuildToolPath() {
        return buildToolPath;
    }

    public void setBuildToolPath(String buildToolPath) {
        this.buildToolPath = buildToolPath;
    }

    public String getBuildWorkingDirectory() {
        return buildWorkingDirectory;
    }

    public void setBuildWorkingDirectory(String buildWorkingDirectory) {
        this.buildWorkingDirectory = buildWorkingDirectory;
    }

    public boolean isPassBuildSettingsInEnvironment() {
        return passBuildSettingsInEnvironment;
    }

    public void setPassBuildSettingsInEnvironment(boolean passBuildSettingsInEnvironment) {
        this.passBuildSettingsInEnvironment = passBuildSettingsInEnvironment;
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        s.addField("buildArgumentsString", buildArgumentsString);
        s.addField("buildToolPath", buildToolPath);
        if (buildWorkingDirectory != null) {
            s.addField("buildWorkingDirectory", buildWorkingDirectory);
        }
        s.addField("passBuildSettingsInEnvironment", passBuildSettingsInEnvironment ? "1" : "0");
    }
}
