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
import org.gradle.platform.Architecture;
import org.gradle.platform.OperatingSystem;

import javax.inject.Inject;

public abstract class CustomBuildPlatform extends DefaultBuildPlatform {

    private Supplier<Architecture> architecture;

    private Supplier<OperatingSystem> operatingSystem;

    @Inject
    public CustomBuildPlatform(Architecture architecture, OperatingSystem operatingSystem) {
        this.architecture = Suppliers.memoize(() -> architecture);
        this.operatingSystem = Suppliers.memoize(() -> operatingSystem);
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
