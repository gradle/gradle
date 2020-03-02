/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;

public abstract class AbstractProperty<T> extends AbstractMinimalProvider<T> implements PropertyInternal<T> {
    private enum State {
        ImplicitValue, ExplicitValue, Final
    }

    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this property");
    private static final DisplayName DEFAULT_VALIDATION_DISPLAY_NAME = Describables.of("a property");

    private final PropertyHost host;
    private State state = State.ImplicitValue;
    private boolean finalizeOnNextGet;
    private boolean disallowChanges;
    private boolean disallowUnsafeRead;
    private Task producer;
    private DisplayName displayName;

    public AbstractProperty(PropertyHost host) {
        this.host = host;
    }

    @Override
    public void attachDisplayName(DisplayName displayName) {
        this.displayName = displayName;
    }

    @Nullable @Override
    protected DisplayName getDeclaredDisplayName() {
        return displayName;
    }

    @Override
    protected DisplayName getTypedDisplayName() {
        return DEFAULT_DISPLAY_NAME;
    }

    @Override
    protected DisplayName getDisplayName() {
        if (displayName == null) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName;
    }

    protected DisplayName getValidationDisplayName() {
        if (displayName == null) {
            return DEFAULT_VALIDATION_DISPLAY_NAME;
        }
        return displayName;
    }

    @Override
    public void attachProducer(Task task) {
        if (this.producer != null && this.producer != task) {
            throw new IllegalStateException(String.format("%s already has a producer task associated with it.", getDisplayName().getCapitalizedDisplayName()));
        }
        this.producer = task;
    }

    protected abstract ValueSupplier getSupplier();

    /**
     * Returns a diagnostic string describing the current source of value of this property. Should not realize the value.
     */
    protected abstract String describeContents();

    // This method is final - implement describeContents() instead
    @Override
    public final String toString() {
        if (displayName != null) {
            return displayName.toString();
        } else {
            return describeContents();
        }
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
        if (producer != null) {
            visitor.execute(producer);
        } else {
            getSupplier().visitProducerTasks(visitor);
        }
    }

    @Override
    public boolean isValueProducedByTask() {
        return getSupplier().isValueProducedByTask();
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        if (producer != null) {
            context.add(producer);
            return true;
        }
        return getSupplier().maybeVisitBuildDependencies(context);
    }

    @Override
    public void finalizeValue() {
        if (state != State.Final) {
            makeFinal();
        }
        state = State.Final;
        disallowChanges = true;
    }

    @Override
    public void disallowChanges() {
        disallowChanges = true;
    }

    @Override
    public void finalizeValueOnRead() {
        finalizeOnNextGet = true;
    }

    @Override
    public void implicitFinalizeValue() {
        disallowChanges = true;
        finalizeOnNextGet = true;
    }

    @Override
    public void disallowUnsafeRead() {
        disallowUnsafeRead = true;
    }

    protected abstract void applyDefaultValue();

    protected abstract void makeFinal();

    /**
     * Call prior to reading the value of this property.
     */
    protected void beforeRead() {
        if (state == State.Final) {
            return;
        }
        if (finalizeOnNextGet) {
            makeFinal();
            state = State.Final;
        } else if (disallowUnsafeRead) {
            String reason = host.beforeRead();
            if (reason != null) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot query the value of ");
                formatter.append(getDisplayName().getDisplayName());
                formatter.append(" because ");
                formatter.append(reason);
                formatter.append(".");
                throw new IllegalStateException(formatter.toString());
            }
        }
    }

    /**
     * Call prior to mutating the value of this property.
     */
    protected boolean beforeMutate() {
        if (canMutate()) {
            if (state == State.ImplicitValue) {
                applyDefaultValue();
                state = State.ExplicitValue;
            }
            return true;
        }
        return false;
    }

    /**
     * Call prior to discarding the value of this property.
     */
    protected boolean beforeReset() {
        if (canMutate()) {
            state = State.ImplicitValue;
            return true;
        }
        return false;
    }

    /**
     * Call prior to applying a convention to this property.
     */
    protected boolean shouldApplyConvention() {
        if (canMutate()) {
            return state == State.ImplicitValue;
        }
        return false;
    }

    private boolean canMutate() {
        if (state == State.Final) {
            throw new IllegalStateException(String.format("The value for %s is final and cannot be changed any further.", getDisplayName().getDisplayName()));
        } else if (disallowChanges) {
            throw new IllegalStateException(String.format("The value for %s cannot be changed any further.", getDisplayName().getDisplayName()));
        }
        return true;
    }
}
