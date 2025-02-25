/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.platform.internal;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.GradleException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.BuildPlatformFactory;
import org.gradle.platform.OperatingSystem;

import javax.inject.Inject;

/**
 * Information about the machine host Gradle is running on.
 */
@ServiceScope(Scope.Global.class)
public class CurrentBuildPlatform {

    private final Supplier<Architecture> architecture;

    private final OperatingSystem operatingSystem;

    @Inject
    public CurrentBuildPlatform(final SystemInfo systemInfo, final org.gradle.internal.os.OperatingSystem operatingSystem) {
        this.architecture = Suppliers.memoize(new Supplier<Architecture>() {
            @Override
            public Architecture get() {
                return getArchitecture(systemInfo);
            }
        });
        this.operatingSystem = getOperatingSystem(operatingSystem);
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public Architecture getArchitecture() {
        return architecture.get();
    }

    private static Architecture getArchitecture(SystemInfo systemInfo) {
        SystemInfo.Architecture architecture = systemInfo.getArchitecture();
        switch (architecture) {
            case i386:
                return Architecture.X86;
            case amd64:
                return Architecture.X86_64;
            case aarch64:
                return Architecture.AARCH64;
        }
        throw new GradleException("Unhandled system architecture: " + architecture);
    }

    public static OperatingSystem getOperatingSystem(org.gradle.internal.os.OperatingSystem operatingSystem) {
        if (org.gradle.internal.os.OperatingSystem.LINUX == operatingSystem) {
            return OperatingSystem.LINUX;
        } else if (org.gradle.internal.os.OperatingSystem.UNIX == operatingSystem) {
            return OperatingSystem.UNIX;
        } else if (org.gradle.internal.os.OperatingSystem.WINDOWS == operatingSystem) {
            return OperatingSystem.WINDOWS;
        } else if (org.gradle.internal.os.OperatingSystem.MAC_OS == operatingSystem) {
            return OperatingSystem.MAC_OS;
        } else if (org.gradle.internal.os.OperatingSystem.SOLARIS == operatingSystem) {
            return OperatingSystem.SOLARIS;
        } else if (org.gradle.internal.os.OperatingSystem.FREE_BSD == operatingSystem) {
            return OperatingSystem.FREE_BSD;
        } else {
            throw new GradleException("Unhandled operating system: " + operatingSystem.getName());
        }
    }

    public BuildPlatform toBuildPlatform() {
        return BuildPlatformFactory.of(getArchitecture(), getOperatingSystem());
    }
}
