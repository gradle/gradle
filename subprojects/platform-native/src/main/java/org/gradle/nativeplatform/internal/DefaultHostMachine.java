/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.nativeplatform.HostMachine;
import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

public class DefaultHostMachine implements HostMachine {
    private final TargetMachine hostMachine;

    public DefaultHostMachine(ObjectFactory objectFactory) {
        DefaultNativePlatform host = DefaultNativePlatform.host();
        OperatingSystemFamily operatingSystemFamily = objectFactory.named(OperatingSystemFamily.class, host.getOperatingSystem().toFamilyName());
        MachineArchitecture machineArchitecture = objectFactory.named(MachineArchitecture.class, host.getArchitecture().getName());
        this.hostMachine = new DefaultTargetMachine(operatingSystemFamily, machineArchitecture);
    }

    @Override
    public MachineArchitecture getArchitecture() {
        return hostMachine.getArchitecture();
    }

    @Override
    public OperatingSystemFamily getOperatingSystemFamily() {
        return hostMachine.getOperatingSystemFamily();
    }
}
