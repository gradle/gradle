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

package org.gradle.testing.base.internal;

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.testing.base.MatrixContainer;
import org.gradle.testing.base.MatrixCoordinates;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DefaultMatrixContainer<V extends MatrixContainer.MatrixValue> implements MatrixContainer<V> {
    // TODO: move `Specs` out of base-services-groovy (???) and use here
    private static final Spec<MatrixCoordinates> ALWAYS_SATISFIED = element -> true;

    private Map<Spec<? super MatrixCoordinates>, ImmutableActionSet<V>> specActions =
        new HashMap<Spec<? super MatrixCoordinates>, ImmutableActionSet<V>>();
    private Map<Spec<? super MatrixCoordinates>, Throwable> requiredSpecs = new HashMap<>();

    private void checkUnused() {
        if (specActions == null) {
            throw new IllegalStateException("Cannot add actions to a matrix after it has been used");
        }
    }

    @Override
    public void all(Action<? super V> action) {
        matching(ALWAYS_SATISFIED, action);
    }

    @Override
    public void matching(Spec<? super MatrixCoordinates> spec, Action<? super V> action) {
        checkUnused();
        ImmutableActionSet<V> actions = specActions.get(spec);
        if (actions == null) {
            actions = ImmutableActionSet.empty();
        }
        actions = actions.add(action);
        specActions.put(spec, actions);
    }

    @Override
    public void require(Spec<? super MatrixCoordinates> spec, Action<? super V> action) {
        matching(spec, action);
        requiredSpecs.put(spec, new Throwable("Stacktrace for declaration of spec: " + spec));
    }

    public void applyConfigurationTo(Iterator<V> targets) {
        Map<Spec<? super MatrixCoordinates>, ImmutableActionSet<V>> specActions = this.specActions;
        this.specActions = null;
        Map<Spec<? super MatrixCoordinates>, Throwable> requiredSpecs = this.requiredSpecs;
        this.requiredSpecs = null;
        V target;
        while (targets.hasNext()) {
            target = targets.next();
            for (Map.Entry<Spec<? super MatrixCoordinates>, ImmutableActionSet<V>> entry : specActions.entrySet()) {
                if (entry.getKey().isSatisfiedBy(target.getCoordinates())) {
                    requiredSpecs.remove(entry.getKey());
                    entry.getValue().execute(target);
                }
            }
        }
        if (!requiredSpecs.isEmpty()) {
            IllegalStateException ex = new IllegalStateException("Required matrix dimensions were not satisfied: " + requiredSpecs);
            for (Throwable cause : requiredSpecs.values()) {
                ex.addSuppressed(cause);
            }
            throw ex;
        }
    }
}
