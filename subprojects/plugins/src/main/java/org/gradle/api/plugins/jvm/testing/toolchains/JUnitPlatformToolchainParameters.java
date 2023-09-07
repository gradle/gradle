/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm.testing.toolchains;

import org.gradle.api.Action;
import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngine;
import org.gradle.api.plugins.jvm.internal.testing.engines.JUnitPlatformTestEngineFactory;
import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngineParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.inject.Inject;
import java.util.Optional;

abstract public class JUnitPlatformToolchainParameters implements JVMTestToolchainParameters {
    abstract public Property<String> getPlatformVersion();

    abstract public SetProperty<JUnitPlatformTestEngine<?>> getEngines();

    @Inject
    abstract protected JUnitPlatformTestEngineFactory getEngineFactory();

    public <P extends JUnitPlatformTestEngineParameters<?>> void addEngine(Class<? extends JUnitPlatformTestEngine<P>> engineClass) {
        addEngine(engineClass, Optional.empty());
    }

    public <P extends JUnitPlatformTestEngineParameters<?>> void addEngine(Class<? extends JUnitPlatformTestEngine<P>> engineClass, Action<P> action) {
        addEngine(engineClass, Optional.of(action));
    }

    private <P extends JUnitPlatformTestEngineParameters<?>> void addEngine(Class<? extends JUnitPlatformTestEngine<P>> engineClass, Optional<Action<P>> optionalAction) {
        JUnitPlatformTestEngine<P> engine = getEngineFactory().create(engineClass);
        getEngines().add(engine);
        optionalAction.ifPresent(action -> action.execute(engine.getParameters()));
    }
}
