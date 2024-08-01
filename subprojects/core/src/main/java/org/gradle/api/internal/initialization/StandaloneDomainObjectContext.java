/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.ModelContainer;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Domain object context implementation intended for identifying contexts that wrap no mutable state.
 */
public abstract class StandaloneDomainObjectContext implements DomainObjectContext, ModelContainer<Object> {
    private static final Object MODEL = new Object();

    /**
     * A domain object context not tied to any mutable state.
     *
     * NOTE: In almost all cases, other than testing, there is a better domain object context to use.
     */
    public static final StandaloneDomainObjectContext ANONYMOUS = new StandaloneDomainObjectContext() {
        @Override
        public String getDisplayName() {
            return "unknown";
        }
    };

    /**
     * Domain object context for resolving plugins outside a project's buildscript.
     */
    public static final StandaloneDomainObjectContext PLUGINS = new StandaloneDomainObjectContext() {
        @Override
        public boolean isPluginContext() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "plugin resolution";
        }
    };

    /**
     * A domain object context for resolution within some script.
     *
     * TODO: We should probably have more specialized types for each script, to ensure locks
     * on mutable state are properly acquired, build ID is properly reported, etc.
     */
    public static StandaloneDomainObjectContext forScript(ScriptSource source) {
        return new StandaloneDomainObjectContext() {
            @Override
            public String getDisplayName() {
                return source.getShortDisplayName().getDisplayName();
            }
        };
    }

    private StandaloneDomainObjectContext() {
    }

    @Override
    public Path identityPath(String name) {
        return Path.path(name);
    }

    @Override
    public Path projectPath(String name) {
        return Path.path(name);
    }

    @Nullable
    @Override
    public ProjectIdentity getProjectIdentity() {
        return null;
    }

    @Nullable
    @Override
    public ProjectInternal getProject() {
        return null;
    }

    @Override
    public ModelContainer<Object> getModel() {
        return this;
    }

    @Override
    public boolean hasMutableState() {
        return true;
    }

    @Override
    public <S> S fromMutableState(Function<? super Object, ? extends S> factory) {
        return factory.apply(MODEL);
    }

    @Override
    public <S> S forceAccessToMutableState(Function<? super Object, ? extends S> factory) {
        return factory.apply(MODEL);
    }

    @Override
    public void applyToMutableState(Consumer<? super Object> action) {
        action.accept(MODEL);
    }

    @Override
    public Path getBuildPath() {
        return Path.ROOT;
    }

    @Override
    public boolean isScript() {
        return true;
    }

    @Override
    public boolean isRootScript() {
        return true;
    }

    @Override
    public boolean isPluginContext() {
        return false;
    }

    @Override
    public <T> CalculatedModelValue<T> newCalculatedValue(@Nullable T initialValue) {
        return new CalculatedModelValueImpl<>(initialValue);
    }

    private static class CalculatedModelValueImpl<T> implements CalculatedModelValue<T> {
        private volatile T value;

        CalculatedModelValueImpl(@Nullable T initialValue) {
            value = initialValue;
        }

        @Override
        public T get() throws IllegalStateException {
            T currentValue = getOrNull();
            if (currentValue == null) {
                throw new IllegalStateException("No value is available.");
            }
            return currentValue;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public void set(T newValue) {
            value = newValue;
        }

        @Override
        public T update(Function<T, T> updateFunction) {
            synchronized (this) {
                T newValue = updateFunction.apply(value);
                value = newValue;
                return newValue;
            }
        }
    }
}
