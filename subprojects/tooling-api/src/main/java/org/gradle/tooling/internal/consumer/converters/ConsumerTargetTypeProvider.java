/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.internal.adapter.TargetTypeProvider;
import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.gradle.tooling.model.internal.outcomes.GradleFileBuildOutcome;

import java.util.HashMap;
import java.util.Map;

public class ConsumerTargetTypeProvider implements TargetTypeProvider {

    Map<String, Class<?>> configuredTargetTypes = new HashMap<String, Class<?>>();

    public ConsumerTargetTypeProvider() {
        configuredTargetTypes.put(IdeaSingleEntryLibraryDependency.class.getCanonicalName(), IdeaSingleEntryLibraryDependency.class);
        configuredTargetTypes.put(IdeaModuleDependency.class.getCanonicalName(), IdeaModuleDependency.class);
        configuredTargetTypes.put(GradleFileBuildOutcome.class.getCanonicalName(), GradleFileBuildOutcome.class);
    }

    public <T> Class<? extends T> getTargetType(Class<T> initialTargetType, Object protocolObject) {
        Class<?>[] interfaces = protocolObject.getClass().getInterfaces();
        for (Class<?> i : interfaces) {
            if (configuredTargetTypes.containsKey(i.getName())) {
                return configuredTargetTypes.get(i.getName()).asSubclass(initialTargetType);
            }
        }

        return initialTargetType;
    }
}