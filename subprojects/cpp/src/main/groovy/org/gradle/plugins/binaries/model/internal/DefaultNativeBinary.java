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

package org.gradle.plugins.binaries.model.internal;

import org.gradle.plugins.binaries.model.*;

import java.util.List;

public abstract class DefaultNativeBinary implements NativeBinary {
    private final NativeComponent component;

    public DefaultNativeBinary(NativeComponent component) {
        this.component = component;
    }

    public NativeComponent getComponent() {
        return component;
    }

    // TODO:DAZ Allow args to be overridden on a per-binary basis
    // Note that the args collection is not copied, but is wired directly into the compile task (not good)
    public List<Object> getCompilerArgs() {
        return component.getCompilerArgs();
    }
}
