/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.tasks.InputChangesAwareTaskAction;
import org.gradle.api.internal.tasks.TaskRequiredServices;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.ServiceReferenceSpec;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.VerificationException;
import org.gradle.internal.Factory;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.util.Configurable;
import org.gradle.util.Path;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TaskInternal extends Task, Configurable<Task> {

    /**
     * A more efficient version of {@link #getActions()}, which circumvents the
     * validating change listener that normally prevents users from changing tasks
     * once they start executing.
     */
    @Internal
    List<InputChangesAwareTaskAction> getTaskActions();

    /**
     * Sets the task actions for this task. Used by the configuration cache when restoring the task.
     *
     * @param taskActions the actions to restore
     */
    void restoreTaskActions(List<InputChangesAwareTaskAction> taskActions);

    @Internal
    boolean hasTaskActions();

    @Internal
    Spec<? super TaskInternal> getOnlyIf();

    /**
     * Sets the only-if condition for this task without any decoration. Used by the configuration cache when restoring the task.
     *
     * @param onlyIf the spec to restore
     */
    void restoreOnlyIf(Spec<? super TaskInternal> onlyIf);

    /**
     * Returns the combined reasons to not track state in a single String.
     *
     * Gradle considers the task as untracked if the reason is present.
     * When not tracking state, a reason must be present. Hence the {@code Optional} represents the state of enablement, too.
     *
     * @see org.gradle.api.tasks.UntrackedTask
     */
    @Internal
    Optional<String> getReasonNotToTrackState();

    /**
     * Returns the individual reasons to not track the task state.  This is used for serializing the reasons to the
     * configuration cache.
     */
    @Internal
    Set<String> getReasonsNotToTrackState();

    /**
     * Sets a condition to not track the task state when the given spec matches.  This condition will be evaluated when the return
     * value of {@link #getReasonNotToTrackState()} is queried.
     *
     * @param reason - the reason for not tracking the task state when the condition matches
     * @param spec - the condition to evaluate
     */
    void doNotTrackStateIf(String reason, Spec<? super TaskInternal> spec);

    @Internal
    boolean isCompatibleWithConfigurationCache();

    @Internal
    Optional<String> getReasonTaskIsIncompatibleWithConfigurationCache();

    @Internal
    StandardOutputCapture getStandardOutputCapture();

    @Override
    TaskInputsInternal getInputs();

    @Override
    TaskOutputsInternal getOutputs();

    @Override
    TaskStateInternal getState();

    @Internal
    boolean getImpliesSubProjects();

    void setImpliesSubProjects(boolean impliesSubProjects);

    /**
     * The returned factory is expected to return the same file each time.
     * <p>
     * The getTemporaryDir() method creates the directory which can be problematic. Use this to delay that creation.
     */
    @Internal
    Factory<File> getTemporaryDirFactory();

    void prependParallelSafeAction(Action<? super Task> action);

    void appendParallelSafeAction(Action<? super Task> action);

    @Internal
    boolean isHasCustomActions();

    @Internal
    Path getIdentityPath();

    @Internal
    TaskIdentity<?> getTaskIdentity();

    @Internal
    TaskRequiredServices getRequiredServices();

    void acceptServiceReferences(Set<ServiceReferenceSpec> serviceReferences);

    /**
     * <p>Gets the shared resources required by this task.</p>
     */
    @Internal
    List<? extends ResourceLock> getSharedResources();

    /**
     * "Lifecycle dependencies" are dependencies of this task declared via an explicit {@link Task#dependsOn(Object...)} call,
     * as opposed to the recommended approach of connecting producer tasks' outputs to consumer tasks' inputs.
     * @return the dependencies of this task declared via an explicit {@link Task#dependsOn(Object...)}
     */
    @Internal
    TaskDependency getLifecycleDependencies();

    /**
     * Build a failure message for display when this task fails execution.
     * <p>
     * This includes task provenance information to help diagnose the source of the failing task.
     *
     * @return the constructed failure message
     */
    @Internal
    default String buildFailureMessage(Throwable cause) {
        boolean isVerificationFailure = cause instanceof VerificationException;
        String createdBy = "";
        if (isVerificationFailure) {
            String provenance = getProvenance().orElse(null);
            if (provenance != null) {
                createdBy = String.format(" (%s)", provenance);
            }
        }
        return String.format("Execution failed for %s%s.", this, createdBy);
    }

    @Internal
    default Optional<String> getProvenance() {
        UserCodeSource source = getTaskIdentity().getUserCodeSource();
        String sourceDesc = source.getDisplayName().getDisplayName();

        boolean isUnknownSource = source == UserCodeSource.UNKNOWN;
        boolean isCreatedByRule = source == UserCodeSource.BY_RULE;
        if (isUnknownSource) {
            return Optional.empty();
        } else if (isCreatedByRule) {
            return Optional.of("registered by Rule");
        } else {
            boolean isPluginSource = sourceDesc.contains("plugin");
            String preposition = isPluginSource ? "by" : "in";
            return Optional.of(String.format("registered %s %s", preposition, sourceDesc));
        }
    }
}
