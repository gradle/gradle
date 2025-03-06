/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.OperatingSystem;

import java.io.Serializable;
import java.util.Objects;

public class DefaultBuildPlatform implements BuildPlatform, Serializable {

    private final Architecture architecture;
    private final OperatingSystem operatingSystem;

    public DefaultBuildPlatform(final Architecture architecture, final OperatingSystem operatingSystem) {
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
    }

    @Override
    public Architecture getArchitecture() {
        return architecture;
    }

    @Override
    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultBuildPlatform)) {
            return false;
        }
        DefaultBuildPlatform that = (DefaultBuildPlatform) o;
        return Objects.equals(getArchitecture(), that.getArchitecture()) && Objects.equals(getOperatingSystem(), that.getOperatingSystem());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArchitecture(), getOperatingSystem());
    }
}
