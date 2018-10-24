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

    @Override public TargetMachine host() {
        DefaultNativePlatform host = DefaultNativePlatform.host();
        OperatingSystemFamily operatingSystemFamily = objectFactory.named(OperatingSystemFamily.class, host.getOperatingSystem().toFamilyName());
        MachineArchitecture machineArchitecture = objectFactory.named(MachineArchitecture.class, host.getArchitecture().getName());
        return new TargetMachineImpl(operatingSystemFamily, machineArchitecture);
    }

    @Override
    public TargetMachine windows() {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, OperatingSystemFamily.WINDOWS), getDefaultArchitecture());
    }

    @Override
    public TargetMachine linux() {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, OperatingSystemFamily.LINUX), getDefaultArchitecture());
    }

    @Override
    public TargetMachine macos() {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, OperatingSystemFamily.MACOS), getDefaultArchitecture());
    }

    @Override
    public TargetMachine of(String operatingSystemFamily) {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, operatingSystemFamily), getDefaultArchitecture());
    }

    @Override
    public TargetMachine of(String operatingSystemFamily, String architecture) {
        return new TargetMachineImpl(objectFactory.named(OperatingSystemFamily.class, operatingSystemFamily), objectFactory.named(MachineArchitecture.class, architecture));
    }

    private MachineArchitecture getDefaultArchitecture() {
        return objectFactory.named(MachineArchitecture.class, DefaultNativePlatform.host().getName());
    }

    private class TargetMachineImpl implements TargetMachine {
        private final OperatingSystemFamily operatingSystemFamily;
        private final MachineArchitecture architecture;

        public TargetMachineImpl(OperatingSystemFamily operatingSystemFamily, MachineArchitecture architecture) {
            this.operatingSystemFamily = operatingSystemFamily;
            this.architecture = architecture;
        }

        @Override
        public OperatingSystemFamily getOperatingSystemFamily() {
            return operatingSystemFamily;
        }

        @Override
        public MachineArchitecture getArchitecture() {
            return architecture;
        }

        @Override
        public TargetMachine x86() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.X86));
        }

        @Override
        public TargetMachine x64() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.X64));
        }

        @Override
        public TargetMachine ia64() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.IA64));
        }

        @Override
        public TargetMachine arm() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.ARM));
        }

        @Override
        public TargetMachine arm64() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.ARM64));
        }

        @Override
        public TargetMachine ppc() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.PPC));
        }

        @Override
        public TargetMachine ppc64() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.PPC64));
        }

        @Override
        public TargetMachine sparc() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.SPARC));
        }

        @Override
        public TargetMachine sparc64() {
            return new TargetMachineImpl(operatingSystemFamily, objectFactory.named(MachineArchitecture.class, MachineArchitecture.SPARC64));
        }
    }
}
