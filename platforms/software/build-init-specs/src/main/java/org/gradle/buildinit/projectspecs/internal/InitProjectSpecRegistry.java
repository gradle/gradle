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

package org.gradle.buildinit.projectspecs.internal;

import org.gradle.api.Action;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.buildinit.projectspecs.InitAction;
import org.gradle.buildinit.projectspecs.InitParameters;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@ServiceScope(Scope.Build.class)
public abstract class InitProjectSpecRegistry {
    private final Map<String, InitGenerator> registrations = new HashMap<>();

    private final class Registration<G extends InitAction<P>, P extends InitParameters> implements InitGenerator {
        private final String displayName;
        private final Class<G> generatorType;
        private final Class<P> parameterType;
        private final Action<? super P> configuration;

        private Registration(String displayName, Class<G> generatorType, Class<P> parameterType, Action<? super P> configuration) {
            this.displayName = displayName;
            this.generatorType = generatorType;
            this.parameterType = parameterType;
            this.configuration = configuration;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public void generate(Directory projectDir) {
            // TODO: Isolate generator and spec?
            P parameters = getObjectFactory().newInstance(parameterType);
            parameters.getProjectDirectory().convention(projectDir);
            configuration.execute(parameters);
            G generator = getObjectFactory().newInstance(generatorType, parameters);
            generator.execute();
        }
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public <T extends InitParameters> void register(String displayName, Class<? extends InitAction<T>> generator, Class<T> parameters, Action<? super T> configuration) {
        if (registrations.containsKey(displayName)) {
            throw new IllegalArgumentException("A generator with the display name '" + displayName + "' is already registered.");
        }
        registrations.put(displayName, new Registration<>(displayName, generator, parameters, configuration));
    }

    public Map<String, InitGenerator> getRegistrations() {
        return registrations;
    }
}
