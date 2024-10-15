/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.GradleException;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.Architecture;
import org.gradle.platform.OperatingSystem;

import javax.inject.Inject;

public class DefaultBuildPlatform implements BuildPlatform {

    private Architecture architecture;

    private Supplier<OperatingSystem> operatingSystem;

    @Inject
    public DefaultBuildPlatform(Architecture architecture, final org.gradle.internal.os.OperatingSystem operatingSystem) {
        this.architecture = architecture;
        this.operatingSystem = Suppliers.memoize(new Supplier<OperatingSystem>() {
            @Override
            public OperatingSystem get() {
                return getOperatingSystem(operatingSystem);
            }
        });
    }

    @Override
    public Architecture getArchitecture() {
        return architecture;
    }

    @Override
    public OperatingSystem getOperatingSystem() {
        return operatingSystem.get();
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
        } else  if (org.gradle.internal.os.OperatingSystem.SOLARIS == operatingSystem) {
            return OperatingSystem.SOLARIS;
        } else if (org.gradle.internal.os.OperatingSystem.FREE_BSD == operatingSystem) {
            return OperatingSystem.FREE_BSD;
        } else if (org.gradle.internal.os.OperatingSystem.AIX == operatingSystem) {
            return OperatingSystem.AIX;
        } else {
            throw new GradleException("Unhandled operating system: " + operatingSystem.getName());
        }
    }
}
