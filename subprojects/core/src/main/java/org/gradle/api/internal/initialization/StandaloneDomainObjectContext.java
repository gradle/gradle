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
import org.gradle.internal.model.ModelContainer;
import org.gradle.util.Path;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

    private StandaloneDomainObjectContext() {
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
    public <S> S runWithModelLock(Supplier<S> action) {
        return action.get();
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

}
