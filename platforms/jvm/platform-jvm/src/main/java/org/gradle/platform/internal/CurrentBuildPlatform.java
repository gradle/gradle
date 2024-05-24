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
import net.rubygrapefruit.platform.internal.DefaultSystemInfo;
import org.gradle.platform.Architecture;
import org.gradle.platform.OperatingSystem;

import javax.inject.Inject;

public abstract class CurrentBuildPlatform extends DefaultBuildPlatform {

    private Supplier<Architecture> architecture;

    private Supplier<OperatingSystem> operatingSystem;

    @Inject
    public CurrentBuildPlatform() {
        this.architecture = Suppliers.memoize(() -> getArchitecture(new DefaultSystemInfo()));
        this.operatingSystem = Suppliers.memoize(() -> getOperatingSystem(org.gradle.internal.os.OperatingSystem.current()));
    }

    @Override
    public Architecture getArchitecture() {
        return architecture.get();
    }

    @Override
    public OperatingSystem getOperatingSystem() {
        return operatingSystem.get();
    }

}
