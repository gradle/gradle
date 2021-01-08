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

package org.gradle.buildinit.plugins.internal;

import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public abstract class LanguageLibraryProjectInitDescriptor implements LanguageSpecificProjectGenerator {
    protected String withPackage(InitSettings settings, String className) {
        if (settings.getPackageName().isEmpty()) {
            return className;
        } else {
            return settings.getPackageName() + "." + className;
        }
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return Collections.singleton(ModularizationOption.SINGLE_PROJECT);
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.empty();
    }
}
