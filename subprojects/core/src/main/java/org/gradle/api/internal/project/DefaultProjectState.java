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

package org.gradle.api.internal.project;

import com.google.common.collect.Iterables;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.model.CalculatedModelValue;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.work.WorkerLeaseService;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class DefaultProjectState implements ProjectState, Closeable {

    private final BuildState owner;
    private final ImmutableProjectDescriptor descriptor;
    private final ProjectIdentity identity;
    private final IProjectFactory projectFactory;
    private final ProjectLifecycleController controller;
    private final WorkerLeaseService workerLeaseService;
    private final ProjectStateLookup projectStateLookup;

    private final ResourceLock allProjectsLock;
    private final ResourceLock projectLock;
    private final ResourceLock taskLock;

    private final Set<Thread> canDoAnythingToThisProject = new CopyOnWriteArraySet<>();

    /**
     * Initializes a DefaultProjectState for a specific project within the given build,
     * setting up lifecycle control and obtaining the project's worker locks.
     *
     * @param owner the enclosing BuildState that owns this project state
     * @param descriptor immutable descriptor that identifies and describes the project
     * @param projectFactory factory used to create project model instances
     * @param stateTransitionControllerFactory factory for creating state transition controllers used by the lifecycle controller
     * @param buildServices registry of build-scoped services required by the lifecycle controller
     * @param workerLeaseService service that provides and manages worker/resource locks for project and task execution
     * @param projectStateLookup registry used to resolve other ProjectState instances (parent/children) within the build
     */
    DefaultProjectState(
        BuildState owner,
        ImmutableProjectDescriptor descriptor,
        IProjectFactory projectFactory,
        StateTransitionControllerFactory stateTransitionControllerFactory,
        ServiceRegistry buildServices,
        WorkerLeaseService workerLeaseService,
        ProjectStateLookup projectStateLookup
    ) {
        this.owner = owner;
        this.descriptor = descriptor;
        this.identity = descriptor.getIdentity();
        this.projectFactory = projectFactory;
        this.controller = new ProjectLifecycleController(getDisplayName(), stateTransitionControllerFactory, buildServices);
        this.workerLeaseService = workerLeaseService;
        this.projectStateLookup = projectStateLookup;
        this.allProjectsLock = workerLeaseService.getAllProjectsLock(owner.getIdentityPath());
        this.projectLock = workerLeaseService.getProjectLock(owner.getIdentityPath(), identity.getBuildTreePath());
        this.taskLock = workerLeaseService.getTaskExecutionLock(owner.getIdentityPath(), identity.getBuildTreePath());
    }

    /**
     * Access the build state that owns this project state.
     *
     * @return the owning {@link BuildState}
     */
    @Override
    public BuildState getOwner() {
        return owner;
    }

    /**
     * Gets the identity of this project.
     *
     * @return the ProjectIdentity that uniquely identifies this project
     */

    @Override
    public ProjectIdentity getIdentity() {
        return identity;
    }

    /**
     * Gets the project's directory on disk.
     *
     * @return the project's directory as a {@link File}
     */
    @Override
    public File getProjectDir() {
        return descriptor.getProjectDir();
    }

    /**
     * Create the component identifier for this project.
     *
     * @return the ProjectComponentIdentifier that represents this project
     */
    @Override
    public ProjectComponentIdentifier getComponentIdentifier() {
        return new DefaultProjectComponentIdentifier(identity);
    }

    /**
     * Accesses the project's immutable descriptor.
     *
     * @return the project's {@link ImmutableProjectDescriptor}
     */
    @Override
    public ImmutableProjectDescriptor getDescriptor() {
        return descriptor;
    }

    /**
     * Represent this project state using its display name.
     *
     * @return the project's display name
     */
    @Override
    public String toString() {
        return getDisplayName().getDisplayName();
    }

    // endregion

    /**
     * Retrieve the parent project state when present.
     *
     * @return the parent ProjectState if this project has a parent, or null otherwise
     */

    @Override
    @Nullable
    public ProjectState getParent() {
        ProjectIdentity parentIdentity = descriptor.getParent();
        if (parentIdentity == null) {
            return null;
        }

        return getState(parentIdentity);
    }

    /**
     * Provides the project's child states as a set ordered by each child's identity path.
     *
     * @return a set of this project's child {@code ProjectState} instances sorted by {@code ProjectState#getIdentityPath()}
     */
    @Override
    public Set<ProjectState> getChildProjects() {
        Set<ProjectState> children = new TreeSet<>(Comparator.comparing(ProjectState::getIdentityPath));
        for (ProjectIdentity child : descriptor.getChildren()) {
            children.add(getState(child));
        }
        return children;
    }

    /**
     * Provide an iterable view of this project's child ProjectState instances in unspecified order.
     *
     * @return an Iterable of ProjectState for each child project; the iteration order is unspecified
     */
    @Override
    public Iterable<ProjectState> getUnorderedChildProjects() {
        return Iterables.transform(descriptor.getChildren(), this::getState);
    }

    /**
     * Resolve the ProjectState corresponding to the given project identity's build-tree path.
     *
     * @param identity the project identity whose build-tree path is used to look up the state
     * @return the matching ProjectState
     * @throws IllegalStateException if no project for the identity's build-tree path exists in the registry
     */
    private ProjectState getState(ProjectIdentity identity) {
        ProjectState state = projectStateLookup.findProject(identity.getBuildTreePath());
        if (state == null) {
            throw new IllegalStateException("Project '" + identity.getBuildTreePath() + "' is not found in the registry");
        }
        return state;
    }

    /**
     * Determines whether this project has any child projects.
     *
     * @return `true` if the project has one or more child projects, `false` otherwise.
     */
    @Override
    public boolean hasChildren() {
        return !descriptor.getChildren().isEmpty();
    }

    // endregion

    /**
     * Indicates whether the project's lifecycle has been created.
     *
     * @return `true` if the project has been created, `false` otherwise.
     */

    @Override
    public boolean isCreated() {
        return controller.isCreated();
    }

    /**
     * Create and initialize this project's mutable model using the provided class loader scopes.
     *
     * @param selfClassLoaderScope the class loader scope for the project's own classes
     * @param baseClassLoaderScope the base/parent class loader scope used for shared or parent classes
     */
    @Override
    public void createMutableModel(ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
        controller.createMutableModel(descriptor, owner, this, selfClassLoaderScope, baseClassLoaderScope, projectFactory);
    }

    /**
     * Accesses the project's mutable model.
     *
     * @return the project's mutable ProjectInternal model
     */
    @Override
    public ProjectInternal getMutableModel() {
        return controller.getMutableModel();
    }

    /**
     * Provide access to the project's mutable model even if project configuration failed.
     *
     * @return the project's mutable model instance; may reflect a partially configured or failed configuration state
     */
    @Override
    public ProjectInternal getMutableModelEvenAfterFailure() {
        return controller.getMutableModelEvenAfterFailure();
    }

    /**
     * Ensures this project and its ancestor projects are configured.
     *
     * If the project has a parent, that parent project is configured first; after all ancestors
     * are configured this project’s configuration is performed.
     */
    @Override
    public void ensureConfigured() {
        // Need to configure intermediate parent projects for configure-on-demand
        ProjectState parent = getParent();
        if (parent != null) {
            parent.ensureConfigured();
        }
        controller.ensureSelfConfigured();
    }

    /**
     * Ensures this project's configuration has been performed, verifying the parent is configured first if one exists.
     *
     * <p>If a parent project is present this method asserts the parent is configured before delegating to the controller
     * to ensure this project's own configuration.</p>
     */
    @Override
    public void ensureSelfConfigured() {
        ProjectState parent = getParent();
        if (parent != null) {
            ((DefaultProjectState) parent).controller.assertConfigured();
        }
        controller.ensureSelfConfigured();
    }

    /**
     * Ensures this project's tasks have been discovered.
     *
     * If task discovery has not yet occurred, causes discovery to run so task queries and execution can proceed.
     */
    @Override
    public void ensureTasksDiscovered() {
        controller.ensureTasksDiscovered();
    }

    /**
     * The ResourceLock used to control access to this project's mutable state.
     *
     * @return the ResourceLock that must be held to access or mutate this project's state
     */

    @Override
    public ResourceLock getAccessLock() {
        return projectLock;
    }

    /**
     * Provides the lock that guards task execution for this project.
     *
     * @return the {@link ResourceLock} that must be held to execute or coordinate tasks for this project
     */
    @Override
    public ResourceLock getTaskExecutionLock() {
        return taskLock;
    }

    /**
     * Applies the given action to this project's mutable model.
     *
     * The action is invoked with the current ProjectInternal instance and any return value is discarded.
     *
     * @param action the operation to perform on the project's mutable model
     */
    @Override
    public void applyToMutableState(Consumer<? super ProjectInternal> action) {
        fromMutableState(p -> {
            action.accept(p);
            return null;
        });
    }

    /**
     * Executes the given function with the project's mutable model and returns its result.
     *
     * @param function function that receives the project's mutable model and produces a result
     * @param <S> the result type; may be {@code null}
     * @return the value produced by applying {@code function} to the project's mutable model; may be {@code null}
     */
    @Override
    public <S extends @Nullable Object> S fromMutableState(Function<? super ProjectInternal, ? extends S> function) {
        return runWithModelLock(() -> function.apply(getMutableModel()));
    }

    /**
     * Runs the supplied action while enforcing this project's model-access constraints.
     *
     * <p>The action is executed immediately if the current thread is authorized for unrestricted
     * access or already holds the correct project/all-project lock; otherwise the worker lease
     * service replaces the current locks with this project's lock for the duration of the action.
     *
     * @param action supplier producing the result to compute under the project's model lock
     * @param <S> the result type; may be `null`
     * @return the result produced by the supplier (may be `null`)
     * @throws IllegalStateException if the current thread already holds more than one project lock
     */
    @Override
    public <S extends @Nullable Object> S runWithModelLock(Supplier<S> action) {
        Thread currentThread = Thread.currentThread();
        if (workerLeaseService.isAllowedUncontrolledAccessToAnyProject() || canDoAnythingToThisProject.contains(currentThread)) {
            // Current thread is allowed to access anything at any time, so run the action
            return action.get();
        }

        Collection<? extends ResourceLock> currentLocks = workerLeaseService.getCurrentProjectLocks();
        if (currentLocks.contains(projectLock) || currentLocks.contains(allProjectsLock)) {
            // if we already hold the project lock for this project
            if (currentLocks.size() == 1) {
                // the lock for this project is the only lock we hold, can run the action
                return action.get();
            } else {
                throw new IllegalStateException("Current thread holds more than one project lock. It should hold only one project lock at any given time.");
            }
        } else {
            return workerLeaseService.withReplacedLocks(currentLocks, projectLock, action::get);
        }
    }

    /**
     * Temporarily grants the current thread unrestricted access to this project's mutable model while invoking the supplied function.
     *
     * <p>The supplied {@code factory} is invoked with the project's mutable model and its return value is returned to the caller. The temporary authorization is always revoked after the function returns or throws.</p>
     *
     * @param factory a function that receives the project's mutable model and produces a result
     * @param <S> the result type
     * @return the value produced by {@code factory} when applied to the project's mutable model
     */
    @Override
    public <S> S forceAccessToMutableState(Function<? super ProjectInternal, ? extends S> factory) {
        Thread currentThread = Thread.currentThread();
        boolean added = canDoAnythingToThisProject.add(currentThread);
        try {
            return factory.apply(getMutableModel());
        } finally {
            if (added) {
                canDoAnythingToThisProject.remove(currentThread);
            }
        }
    }

    /**
     * Determines whether the current thread is authorized to access this project's mutable state.
     *
     * @return `true` if the current thread has temporary per-project authorization, global uncontrolled access, or holds the project or all-project lock; `false` otherwise.
     */
    @Override
    public boolean hasMutableState() {
        Thread currentThread = Thread.currentThread();
        if (canDoAnythingToThisProject.contains(currentThread) || workerLeaseService.isAllowedUncontrolledAccessToAnyProject()) {
            return true;
        }
        Collection<? extends ResourceLock> locks = workerLeaseService.getCurrentProjectLocks();
        return locks.contains(projectLock) || locks.contains(allProjectsLock);
    }

    /**
     * Create a new calculated model value associated with this project state.
     *
     * @param initialValue the initial value to store (may be {@code null})
     * @param <T> the value type
     * @return a {@link CalculatedModelValue} that holds and manages a cached calculated value for this project
     */
    @Override
    public <T> CalculatedModelValue<T> newCalculatedValue(@Nullable T initialValue) {
        return new CalculatedModelValueImpl<>(this, workerLeaseService, initialValue);
    }

    /**
     * Closes this project's lifecycle and releases resources associated with it.
     *
     * Once closed, the project state is no longer active for lifecycle operations.
     */
    @Override
    public void close() {
        controller.close();
    }

    private static class CalculatedModelValueImpl<T> implements CalculatedModelValue<T> {
        private final ProjectLeaseRegistry projectLeaseRegistry;
        private final ModelContainer<?> owner;
        private final ReentrantLock lock = new ReentrantLock();
        private volatile @Nullable T value;

        /**
         * Creates a calculated model value tied to a specific project state and registry, initialized to the given value.
         *
         * @param owner the owning DefaultProjectState whose mutable-state access rules govern mutations
         * @param projectLeaseRegistry registry used to perform blocking waits for the update lock without holding project locks
         * @param initialValue the initial value to store; may be {@code null}
         */
        public CalculatedModelValueImpl(DefaultProjectState owner, WorkerLeaseService projectLeaseRegistry, @Nullable T initialValue) {
            this.projectLeaseRegistry = projectLeaseRegistry;
            this.value = initialValue;
            this.owner = owner;
        }

        /**
         * Obtain the calculated value for the owning project, failing if no value has been set.
         *
         * @return the non-null calculated value
         * @throws IllegalStateException if no calculated value is available for the owning project
         */
        @Override
        public T get() throws IllegalStateException {
            T currentValue = getOrNull();
            if (currentValue == null) {
                throw new IllegalStateException("No calculated value is available for " + owner);
            }
            return currentValue;
        }

        /**
         * Retrieve the current cached calculated value without coordinating with in-progress updates.
         *
         * <p>This method returns the value as observed at the time of the call; it does not acquire
         * update locks and may observe concurrent modifications in progress.</p>
         *
         * @return the current cached value, or {@code null} if no value is available
         */
        @Override
        public @Nullable T getOrNull() {
            // Grab the current value, ignore updates that may be happening
            return value;
        }

        /**
         * Replace the stored calculated value for this project.
         *
         * @param newValue the new value to store (may be null)
         * @throws IllegalStateException if the current thread is not authorized to mutate this project's state
         */
        @Override
        public void set(T newValue) {
            assertCanMutate();
            value = newValue;
        }

        /**
         * Applies the given function to the current calculated value and replaces it with the result while serializing concurrent updates.
         *
         * The update is performed only when the caller holds permission to mutate the project's state; concurrent callers are serialized by an internal update lock.
         *
         * @param updateFunction function that produces the new value from the current value
         * @return the new value after applying the update function
         */
        @Override
        public T update(Function<T, T> updateFunction) {
            acquireUpdateLock();
            try {
                T newValue = updateFunction.apply(value);
                value = newValue;
                return newValue;
            } finally {
                releaseUpdateLock();
            }
        }

        /**
         * Acquire the update lock used to serialize calculated-value mutations for this project.
         *
         * If the lock is uncontended, returns immediately and the caller may continue holding project
         * mutation locks. If the lock is contended, waits for the lock via the project lease registry
         * using a blocking strategy that avoids holding project mutation locks while waiting (prevents deadlock).
         *
         * @throws IllegalStateException if the current thread is not permitted to mutate this project's state
         */
        private void acquireUpdateLock() {
            // It's important that we do not block waiting for the lock while holding the project mutation lock.
            // Doing so can lead to deadlocks.

            assertCanMutate();

            if (lock.tryLock()) {
                // Update lock was not contended, can keep holding the project locks
                return;
            }

            // Another thread holds the update lock, release the project locks and wait for the other thread to finish the update
            projectLeaseRegistry.blocking(lock::lock);
        }

        /**
         * Ensures the current thread is allowed to mutate this project's mutable state.
         *
         * @throws IllegalStateException if the current thread does not hold the state lock for this project
         */
        private void assertCanMutate() {
            if (!owner.hasMutableState()) {
                throw new IllegalStateException("Current thread does not hold the state lock for " + owner);
            }
        }

        /**
         * Releases the reentrant lock used to serialize calculated-value updates.
         */
        private void releaseUpdateLock() {
            lock.unlock();
        }
    }
}
