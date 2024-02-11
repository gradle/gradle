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

package org.gradle.nativeplatform.internal;

import org.gradle.api.model.ObjectFactory;
import org.gradle.nativeplatform.TargetMachineBuilder;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

public class DefaultTargetMachineFactory implements TargetMachineFactory {
    private final ObjectFactory objectFactory;

    public DefaultTargetMachineFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    /**
     * Returns a {@link TargetMachine} representing the operating system and architecture of the current host.
     */
    public TargetMachine host() {
        DefaultNativePlatform host = DefaultNativePlatform.host();
        OperatingSystemFamily operatingSystemFamily = objectFactory.named(OperatingSystemFamily.class, host.getOperatingSystem().toFamilyName());
        MachineArchitecture machineArchitecture = objectFactory.named(MachineArchitecture.class, host.getArchitecture().getName());
        return new TargetMachineImpl(operatingSystemFamily, machineArchitecture);
    }

    @Override
    public TargetMachineBuilder getWindows() {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, OperatingSystemFamily.WINDOWS), getDefaultArchitecture());
    }

    @Override
    public TargetMachineBuilder getLinux() {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, OperatingSystemFamily.LINUX), getDefaultArchitecture());
    }

    @Override
    public TargetMachineBuilder getMacOS() {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, OperatingSystemFamily.MACOS), getDefaultArchitecture());
    }

    @Override
    public TargetMachineBuilder os(String operatingSystemFamily) {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, operatingSystemFamily), getDefaultArchitecture());
    }

    private MachineArchitecture getDefaultArchitecture() {
        return objectFactory.named(MachineArchitecture.class, DefaultNativePlatform.host().getArchitecture().getName());
    }

    private class TargetMachineImpl extends DefaultTargetMachine implements TargetMachineBuilder {
        public TargetMachineImpl(OperatingSystemFamily operatingSystemFamily, MachineArchitecture architecture) {
            super(operatingSystemFamily, architecture);
        }

        @Override
        public TargetMachine getX86() {
            return new TargetMachineImpl(getOperatingSystemFamily(), objectFactory.named(MachineArchitecture.class, MachineArchitecture.X86));
        }

        @Override
        public TargetMachine getX86_64() {
            return new TargetMachineImpl(getOperatingSystemFamily(), objectFactory.named(MachineArchitecture.class, MachineArchitecture.X86_64));
        }

        @Override
        public TargetMachine architecture(String architecture) {
            return new TargetMachineImpl(getOperatingSystemFamily(), objectFactory.named(MachineArchitecture.class, architecture));
        }
    }
}
