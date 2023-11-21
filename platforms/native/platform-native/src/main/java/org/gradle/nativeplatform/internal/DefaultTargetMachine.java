/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.nativeplatform.MachineArchitecture;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.TargetMachine;

import java.util.Objects;

public class DefaultTargetMachine implements TargetMachine {
    private final OperatingSystemFamily operatingSystemFamily;
    private final MachineArchitecture architecture;

    public DefaultTargetMachine(OperatingSystemFamily operatingSystemFamily, MachineArchitecture architecture) {
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultTargetMachine that = (DefaultTargetMachine) o;
        return Objects.equals(operatingSystemFamily, that.operatingSystemFamily) &&
                Objects.equals(architecture, that.architecture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operatingSystemFamily, architecture);
    }

    @Override
    public String toString() {
        return operatingSystemFamily.getName() + ":" + architecture.getName();
    }
}
