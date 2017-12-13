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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain;

import java.io.File;

public abstract class ExtendableToolChain<T extends NativePlatformToolChain> implements NativeToolChainInternal {
    private final String name;
    protected final OperatingSystem operatingSystem;
    private final PathToFileResolver fileResolver;
    protected final MutableActionSet<T> configureActions = new MutableActionSet<T>();
    protected final BuildOperationExecutor buildOperationExecutor;

    protected ExtendableToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, PathToFileResolver fileResolver) {
        this.name = name;
        this.operatingSystem = operatingSystem;
        this.fileResolver = fileResolver;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public String getName() {
        return name;
    }

    protected abstract String getTypeName();

    @Override
    public String getDisplayName() {
        return "Tool chain '" + getName() + "' (" + getTypeName() + ")";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getOutputType() {
        return getName() + "-" + operatingSystem.getName();
    }

    public void eachPlatform(Action<? super T> action) {
        configureActions.add(action);
    }

    protected File resolve(Object path) {
        return fileResolver.resolve(path);
    }

}
