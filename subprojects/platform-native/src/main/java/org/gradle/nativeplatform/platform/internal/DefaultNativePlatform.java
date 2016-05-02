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

package org.gradle.nativeplatform.platform.internal;

import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;

public class DefaultNativePlatform implements NativePlatformInternal {
    private final String name;
    private ArchitectureInternal architecture;
    private OperatingSystemInternal operatingSystem;

    public DefaultNativePlatform(String name) {
        this(name, getCurrentOperatingSystem(), getCurrentArchitecture());
    }

    protected DefaultNativePlatform(String name, OperatingSystemInternal operatingSystem, ArchitectureInternal architecture) {
        this.name = name;
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
    }

    private static DefaultOperatingSystem getCurrentOperatingSystem() {
        return new DefaultOperatingSystem(System.getProperty("os.name"), OperatingSystem.current());
    }

    public static ArchitectureInternal getCurrentArchitecture() {
        String architectureName;
        try {
            architectureName = NativeServices.getInstance().get(SystemInfo.class).getArchitectureName();
        } catch (NativeIntegrationUnavailableException e) {
            architectureName = System.getProperty("os.arch");
        }
        return Architectures.forInput(architectureName);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return String.format("platform '%s'", name);
    }

    @Override
    public ArchitectureInternal getArchitecture() {
        return architecture;
    }

    @Override
    public void architecture(String name) {
        architecture = Architectures.forInput(name);
    }

    @Override
    public OperatingSystemInternal getOperatingSystem() {
        return operatingSystem;
    }

    @Override
    public void operatingSystem(String name) {
        operatingSystem = new DefaultOperatingSystem(name);
    }

    public String getCompatibilityString() {
        return getArchitecture().getName() + ":" + getOperatingSystem().getName();
    }
}
