/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

/**
 * Helpers for {@link ProducerAware} implementations, which keep track of the model object that declares them as an output property.
 */
public final class OutputProperties {
    private OutputProperties() {
    }

    /**
     * Fails when a producer other than {@code owner} has already been attached to the object with the given display name.
     *
     * @param current the producer currently attached to the object, if any
     * @param owner the producer about to be attached
     */
    public static void assertCanAttachProducer(@Nullable ModelObject current, ModelObject owner, DisplayName displayName) {
        if (current == null || current == owner) {
            return;
        }
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(displayName.getCapitalizedDisplayName());
        formatter.append(" is already declared as an output property of ");
        describe(current, formatter);
        formatter.append(". Cannot also declare it as an output property of ");
        describe(owner, formatter);
        formatter.append(".");
        throw new IllegalStateException(formatter.toString());
    }

    /**
     * Returns the task that owns the given producer, or {@code null} when there is no producer.
     * Fails when the producer is not owned by any task.
     */
    @Nullable
    public static Task producerTaskOf(@Nullable ModelObject producer, DisplayName displayName) {
        if (producer == null) {
            return null;
        }
        Task task = producer.getTaskThatOwnsThisObject();
        if (task == null) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(displayName.getCapitalizedDisplayName());
            formatter.append(" is declared as an output property of ");
            describe(producer, formatter);
            formatter.append(" but does not have a task associated with it.");
            throw new IllegalStateException(formatter.toString());
        }
        return task;
    }

    private static void describe(ModelObject modelObject, TreeFormatter formatter) {
        if (modelObject.getModelIdentityDisplayName() != null) {
            formatter.append(modelObject.getModelIdentityDisplayName().getDisplayName());
            formatter.append(" (type ");
            formatter.appendType(modelObject.getClass());
            formatter.append(")");
        } else if (modelObject.hasUsefulDisplayName()) {
            formatter.append(modelObject.toString());
            formatter.append(" (type ");
            formatter.appendType(modelObject.getClass());
            formatter.append(")");
        } else {
            formatter.append("an object with type ");
            formatter.appendType(modelObject.getClass());
        }
    }
}
