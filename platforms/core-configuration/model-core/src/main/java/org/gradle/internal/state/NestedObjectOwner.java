/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.state;

import org.gradle.api.Task;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationOwner;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Ownership established by resolving nested properties.
 *
 * <p>Input beans may participate in several declarations. A unique task is required only when
 * an output requests its producer. Retaining all enclosing declarations prevents the last read
 * from silently changing the producer of a shared output.</p>
 *
 * <p>The associations are transient: configuration-cache restoration reattaches the declarations.
 * In particular, a shared input bean must not serialize references to other tasks.</p>
 */
@NullMarked
public final class NestedObjectOwner implements ModelObject, EvaluationOwner {
    private final String displayName;
    @Nullable
    private transient volatile List<ModelObject> owners;
    @Nullable
    private transient Set<ModelObject> ownerIdentities;

    public NestedObjectOwner(ModelObject owner, DisplayName displayName) {
        this.displayName = displayName.getDisplayName();
        this.owners = Collections.singletonList(owner);
    }

    /**
     * Preserves nested declarations when another getter attaches the same bean.
     * Ordinary getter ownership retains its existing behavior.
     */
    @Nullable
    public static ModelObject merge(@Nullable ModelObject previous, @Nullable ModelObject next) {
        if (previous == next || next == null) {
            return next;
        }
        if (previous instanceof NestedObjectOwner) {
            NestedObjectOwner owner = (NestedObjectOwner) previous;
            owner.addOwner(next);
            return owner;
        }
        if (next instanceof NestedObjectOwner && previous != null) {
            ((NestedObjectOwner) next).addOwner(previous);
        }
        return next;
    }

    /**
     * Records an enclosing declaration without querying its owner or inspecting its value.
     */
    public void addOwner(ModelObject owner) {
        if (owner instanceof NestedObjectOwner) {
            List<ModelObject> nestedOwners = ((NestedObjectOwner) owner).owners;
            if (nestedOwners != null) {
                for (ModelObject nestedOwner : nestedOwners) {
                    addSingle(nestedOwner);
                }
            }
        } else {
            addSingle(owner);
        }
    }

    private synchronized void addSingle(ModelObject owner) {
        List<ModelObject> current = owners;
        if (current != null) {
            if (current.get(0) == owner) {
                return;
            }
            if (ownerIdentities == null) {
                ownerIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
                ownerIdentities.addAll(current);
            }
            if (!ownerIdentities.add(owner)) {
                return;
            }
        }
        List<ModelObject> updated = current == null ? new ArrayList<>() : new ArrayList<>(current);
        updated.add(owner);
        owners = Collections.unmodifiableList(updated);
    }

    @Override
    @Nullable
    public Task getTaskThatOwnsThisObject() {
        try (EvaluationScopeContext ignored = EvaluationContext.current().open(this)) {
            Task result = null;
            List<ModelObject> current = owners;
            if (current != null) {
                for (ModelObject owner : current) {
                    Task task = owner.getTaskThatOwnsThisObject();
                    if (task != null) {
                        if (result != null && result != task) {
                            throw new ConflictingOwnersException("Nested output declared by " + displayName
                                + " has more than one producing task: " + result + " and " + task
                                + ". Use a separate output bean for each task.");
                        }
                        result = task;
                    }
                }
            }
            return result;
        }
    }

    @Override
    public DisplayName getModelIdentityDisplayName() {
        return Describables.of(displayName);
    }

    @Override
    public boolean hasUsefulDisplayName() {
        return true;
    }

    @Override
    public void attachModelProperties() {
        // The owning declaration reattaches this context when it supplies the bean.
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Indicates that an output cannot identify a unique producing task.
     */
    public static class ConflictingOwnersException extends IllegalStateException {
        public ConflictingOwnersException(String message) {
            super(message);
        }
    }
}
