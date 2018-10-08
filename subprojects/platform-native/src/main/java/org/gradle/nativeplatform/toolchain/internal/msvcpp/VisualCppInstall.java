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
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.util.VersionNumber;

import javax.annotation.Nullable;
import java.util.Map;

public class VisualCppInstall implements Named {
    private final Map<Architecture, ? extends VisualCpp> platforms;
    private final String name;
    private final VersionNumber version;

    public VisualCppInstall(String name, VersionNumber version,
                            Map<Architecture, ? extends VisualCpp> platforms) {
        this.name = name;
        this.version = version;
        this.platforms = platforms;
    }

    @Override
    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    @Nullable
    public VisualCpp forPlatform(NativePlatformInternal targetPlatform) {
        // TODO:ADAM - ARM only if the target OS is Windows 8 or later
        // TODO:MPUT - ARM also if the target OS is Windows RT or Windows Phone/Mobile/CE
        // TODO:ADAM - IA64 only if the target OS is Windows 2008 or earlier
        if (!targetPlatform.getOperatingSystem().isWindows()) {
            return null;
        }
        return platforms.get(getPlatformArchitecture(targetPlatform));
    }

    private Architecture getPlatformArchitecture(NativePlatformInternal targetPlatform) {
        return targetPlatform.getArchitecture();
    }

    private VisualCpp getDescriptor(NativePlatformInternal targetPlatform) {
        return platforms.get(getPlatformArchitecture(targetPlatform));
    }
}
