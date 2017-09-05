/*
 * Copyright 2015 the original author or authors.
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

import java.io.File;

import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.VersionNumber;

import net.rubygrapefruit.platform.WindowsRegistry;

public class DefaultUcrtLocator extends AbstractWindowsKitLocator<Ucrt> implements UcrtLocator {
    private static final String TOOL_NAME = "ucrt";
    private static final String NAME_USER = "User-provided UCRT";
    private static final String NAME_KIT = "UCRT";

    public DefaultUcrtLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
        super(os, windowsRegistry);
    }

    public Ucrt newComponent(File baseDir, VersionNumber version, DiscoveryType discoveryType) {
        String displayName;
        switch(discoveryType) {
            case USER:
                displayName = NAME_USER;
                break;
            case AUTO:
                displayName = NAME_KIT + " " + version.getMajor();
                break;
            default:
                throw new IllegalArgumentException("Unknown discovery method for " + getComponentName() + ": " + discoveryType);
        }
        return new Ucrt(baseDir, displayName, version);
    }

    public String getComponentName() {
        return TOOL_NAME;
    }
}
