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
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

public class DefaultTargetMachineFactory implements TargetMachineFactory {
    private final ObjectFactory objectFactory;

    public DefaultTargetMachineFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override public TargetMachine host() {
        DefaultNativePlatform host = DefaultNativePlatform.host();
        OperatingSystemFamily operatingSystemFamily = objectFactory.named(OperatingSystemFamily.class, host.getOperatingSystem().toFamilyName());
        return new TargetMachineImpl(operatingSystemFamily, host.getArchitecture());
    }

    private static class TargetMachineImpl implements TargetMachine {
        private final OperatingSystemFamily operatingSystemFamily;
        private final Architecture architecture;

        public TargetMachineImpl(OperatingSystemFamily operatingSystemFamily, Architecture architecture) {
            this.operatingSystemFamily = operatingSystemFamily;
            this.architecture = architecture;
        }

        @Override
        public OperatingSystemFamily getOperatingSystemFamily() {
            return operatingSystemFamily;
        }

        @Override
        public Architecture getArchitecture() {
            return architecture;
        }
    }
}
