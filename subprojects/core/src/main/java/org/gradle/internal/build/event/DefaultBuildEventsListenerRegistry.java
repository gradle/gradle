/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.build.event;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.internal.RegisteredBuildServiceProvider;
import org.gradle.build.event.BuildEventsListenerRegistry;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.event.types.DefaultTaskFinishedProgressEvent;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.tooling.events.OperationCompletionListener;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent;
import org.gradle.tooling.events.task.internal.DefaultTaskOperationDescriptor;
import org.gradle.tooling.internal.consumer.parameters.BuildProgressListenerAdapter;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalTaskResult;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class DefaultBuildEventsListenerRegistry implements BuildEventsListenerRegistry, BuildEventListenerRegistryInternal {
    private static final Logger LOGGER = Logging.getLogger(DefaultBuildEventsListenerRegistry.class);

    // DEBUG (bz-debug-windows-arm-timeout): how long to wait for a build event listener's worker thread to
    // finish draining its queue before forcefully shutting it down. Bumped from 60s to 10m while we
    // troubleshoot the "Timeout waiting for concurrent jobs to complete" shutdown hang.
    private static final int CLOSE_TIMEOUT_MINUTES = 10;

    // DEBUG: interval at which we dump the stuck worker thread's stack while waiting for it to finish.
    private static final long CLOSE_WATCHDOG_INTERVAL_SECONDS = 60;

    private final UserCodeApplicationContext applicationContext;
    private final BuildEventListenerFactory factory;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;
    @GuardedBy("subscriptions")
    private final Map<Provider<?>, AbstractListener<?>> subscriptions = new LinkedHashMap<>();
    private final ExecutorFactory executorFactory;

    public DefaultBuildEventsListenerRegistry(
        UserCodeApplicationContext applicationContext,
        BuildEventListenerFactory factory,
        ListenerManager listenerManager,
        BuildOperationListenerManager buildOperationListenerManager,
        ExecutorFactory executorFactory
    ) {
        this.applicationContext = applicationContext;
        this.factory = factory;
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.executorFactory = executorFactory;
        listenerManager.addListener(new ListenerCleanup());
    }

    @Override
    public List<Subscription> getSubscriptions() {
        synchronized (subscriptions) {
            return subscriptions.entrySet().stream()
                .map(entry -> new Subscription(entry.getValue().getRegistrationPoint(), entry.getKey()))
                .collect(toImmutableList());
        }
    }

    @Override
    public void subscribe(Provider<?> provider) {
        ProviderInternal<?> providerInternal = Providers.internal(provider);
        if (OperationCompletionListener.class.isAssignableFrom(providerInternal.getType())) {
            onTaskCompletion(Cast.uncheckedCast(provider));
        } else {
            onOperationCompletion(Cast.uncheckedCast(provider));
        }
    }

    @Override
    public void onOperationCompletion(Provider<? extends BuildOperationListener> listenerProvider) {
        addSubscriptionIfNotExists(listenerProvider, this::makeBuildOperationSubscription);
    }

    private ForwardingBuildOperationListener makeBuildOperationSubscription(Provider<? extends BuildOperationListener> listenerProvider) {
        ForwardingBuildOperationListener subscription = new ForwardingBuildOperationListener(getCurrentContext(), listenerProvider, executorFactory);
        processIfBuildService(listenerProvider);
        buildOperationListenerManager.addListener(subscription);
        return subscription;
    }

    @Override
    public void onTaskCompletion(Provider<? extends OperationCompletionListener> listenerProvider) {
        addSubscriptionIfNotExists(listenerProvider, this::makeTaskCompletionSubscription);
    }

    private ForwardingBuildEventConsumer makeTaskCompletionSubscription(Provider<? extends OperationCompletionListener> listenerProvider) {
        ForwardingBuildEventConsumer subscription = new ForwardingBuildEventConsumer(getCurrentContext(), listenerProvider, executorFactory);
        processIfBuildService(listenerProvider);

        for (Object listener : subscription.getListeners()) {
            listenerManager.addListener(listener);
            if (listener instanceof BuildOperationListener) {
                buildOperationListenerManager.addListener((BuildOperationListener) listener);
            }
        }
        return subscription;
    }

    private <T> void addSubscriptionIfNotExists(Provider<T> listenerProvider, Function<Provider<T>, ? extends AbstractListener<?>> factoryFunction) {
        synchronized (subscriptions) {
            subscriptions.computeIfAbsent(listenerProvider, Cast.uncheckedNonnullCast(factoryFunction));
        }
    }

    private void processIfBuildService(Provider<?> listenerProvider) {
        if (listenerProvider instanceof RegisteredBuildServiceProvider<?, ?>) {
            RegisteredBuildServiceProvider<?, ?> serviceProvider = Cast.uncheckedCast(listenerProvider);
            serviceProvider.beforeStopping(this::unsubscribeProvider);
            serviceProvider.keepAlive();
        }
    }

    private void unsubscribeProvider(Provider<?> listenerProvider) {
        AbstractListener<?> subscription;
        synchronized (subscriptions) {
            subscription = subscriptions.remove(listenerProvider);
        }
        if (subscription != null) {
            LOGGER.lifecycle("[build-event-listener] Unsubscribing (build service stopping) {}", subscription.getDescription());
            subscription.getListeners().forEach(this::unsubscribe);
            subscription.close();
        }
    }

    private void unsubscribeAll() {
        Collection<AbstractListener<?>> subscribed;

        synchronized (subscriptions) {
            subscribed = ImmutableList.copyOf(subscriptions.values());
            subscriptions.clear();
        }

        LOGGER.lifecycle("[build-event-listener] Unsubscribing all {} remaining listener(s) at build finish: {}",
            subscribed.size(),
            subscribed.stream().map(AbstractListener::getDescription).collect(toImmutableList()));

        subscribed.stream()
            .flatMap(it -> it.getListeners().stream())
            .forEach(this::unsubscribe);
        CompositeStoppable.stoppable(subscribed).stop();

        LOGGER.lifecycle("[build-event-listener] Finished unsubscribing all listeners at build finish");
    }

    private void unsubscribe(Object listener) {
        listenerManager.removeListener(listener);
        if (listener instanceof BuildOperationListener) {
            buildOperationListenerManager.removeListener((BuildOperationListener) listener);
        }
    }

    @Nullable
    private UserCodeSource getCurrentContext() {
        UserCodeApplicationContext.Application current = applicationContext.current();
        return current != null ? current.getSource() : null;
    }

    private static abstract class AbstractListener<T> implements Closeable {
        private static final Object END = new Object();
        // DEBUG: unique id per listener so register/close log lines can be correlated.
        private static final AtomicInteger ID_SEQUENCE = new AtomicInteger();

        private final int id = ID_SEQUENCE.incrementAndGet();
        @Nullable
        private final UserCodeSource registrationPoint;
        private final ManagedExecutor executor;
        private final TransferQueue<Object> events = new LinkedTransferQueue<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        // DEBUG: the worker thread running run(), captured so we can dump its stack if shutdown hangs.
        private volatile Thread workerThread;

        public AbstractListener(@Nullable UserCodeSource registrationPoint, ExecutorFactory executorFactory) {
            this.registrationPoint = registrationPoint;
            // Include the registration point in the executor (thread) name so any thread dump can identify
            // which listener is involved.
            this.executor = executorFactory.create("build event listener " + getDescription());
            LOGGER.lifecycle("[build-event-listener] Registered {}", getDescription());
            Future<?> ignored = executor.submit(this::run);
        }

        @Nullable
        public UserCodeSource getRegistrationPoint() {
            return registrationPoint;
        }

        /**
         * DEBUG: a stable, human-readable identifier for this listener used in troubleshooting logs.
         */
        public String getDescription() {
            String source = registrationPoint == null
                ? "unknown source"
                : registrationPoint.getDisplayName().getDisplayName();
            return getClass().getSimpleName() + "#" + id + " (" + source + ")";
        }

        private void run() {
            workerThread = Thread.currentThread();
            while (true) {
                Object next;
                try {
                    next = events.take();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (next == END) {
                    return;
                }
                try {
                    handle(Cast.uncheckedNonnullCast(next));
                } catch (Throwable t) {
                    failure.set(t);
                    break;
                }
            }
            // A failure has happened. Drain the queue and complete without waiting. There should no more messages added to the queue
            // as the dispatch method will see the failure
            events.clear();
        }

        protected abstract void handle(T message);

        public abstract List<Object> getListeners();

        protected void queue(T message) {
            if (failure.get() == null) {
                events.add(message);
            }
            // else, the handler thread is no longer handling messages so discard it
        }

        @Override
        public void close() {
            events.add(END);
            LOGGER.lifecycle("[build-event-listener] Closing {} (pending events: {}); waiting up to {} minutes for worker to finish",
                getDescription(), events.size(), CLOSE_TIMEOUT_MINUTES);
            long startNanos = System.nanoTime();
            Thread watchdog = startCloseWatchdog();
            try {
                executor.stop(CLOSE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                LOGGER.lifecycle("[build-event-listener] Closed {} in {} ms", getDescription(), elapsedMillis(startNanos));
            } catch (RuntimeException e) {
                // Most likely "Timeout waiting for concurrent jobs to complete" - the worker thread is stuck,
                // typically inside user code invoked from handle(). Dump what it (and every other thread) is
                // doing so we can identify the culprit, then rethrow to preserve existing behaviour.
                LOGGER.error("[build-event-listener] Timed out after {} minutes closing {} (waited {} ms). "
                        + "The worker thread did not finish draining its event queue. Thread dump follows:\n{}",
                    CLOSE_TIMEOUT_MINUTES, getDescription(), elapsedMillis(startNanos), fullThreadDump(), e);
                throw e;
            } finally {
                watchdog.interrupt();
            }
            Throwable failure = this.failure.get();
            if (failure != null) {
                LOGGER.lifecycle("[build-event-listener] {} reported a failure during dispatch; rethrowing", getDescription());
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }

        /**
         * DEBUG: starts a daemon thread that periodically dumps the worker thread's stack while we wait for it
         * to finish, so a hang shows up incrementally instead of only at the final timeout. Interrupted (and so
         * stops) as soon as {@link #close()} completes.
         */
        private Thread startCloseWatchdog() {
            Thread watchdog = new Thread(() -> {
                long startNanos = System.nanoTime();
                try {
                    while (true) {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(CLOSE_WATCHDOG_INTERVAL_SECONDS));
                        Thread worker = workerThread;
                        LOGGER.lifecycle("[build-event-listener] Still waiting ({} ms) for {} to finish. Worker thread '{}' stack:\n{}",
                            elapsedMillis(startNanos), getDescription(), worker == null ? "<not started>" : worker.getName(), stackTraceOf(worker));
                    }
                } catch (InterruptedException ignored) {
                    // close() finished (or timed out) - stop dumping
                }
            }, "build event listener close watchdog " + getDescription());
            watchdog.setDaemon(true);
            watchdog.start();
            return watchdog;
        }

        private static long elapsedMillis(long startNanos) {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        }

        private static String stackTraceOf(@Nullable Thread thread) {
            if (thread == null) {
                return "    <worker thread not available>";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("    ").append(thread).append(" state=").append(thread.getState()).append('\n');
            for (StackTraceElement element : thread.getStackTrace()) {
                sb.append("        at ").append(element).append('\n');
            }
            return sb.toString();
        }

        private static String fullThreadDump() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                sb.append('"').append(thread.getName()).append("\" state=").append(thread.getState()).append('\n');
                for (StackTraceElement element : entry.getValue()) {
                    sb.append("        at ").append(element).append('\n');
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    private static class ForwardingBuildOperationListener extends AbstractListener<Pair<BuildOperationDescriptor, OperationFinishEvent>> implements BuildOperationListener {
        private final Provider<? extends BuildOperationListener> listenerProvider;

        public ForwardingBuildOperationListener(
            @Nullable UserCodeSource registrationPoint,
            Provider<? extends BuildOperationListener> listenerProvider,
            ExecutorFactory executorFactory
        ) {
            super(registrationPoint, executorFactory);
            this.listenerProvider = listenerProvider;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            queue(Pair.of(buildOperation, finishEvent));
        }

        @Override
        protected void handle(Pair<BuildOperationDescriptor, OperationFinishEvent> message) {
            listenerProvider.get().finished(message.left, message.right);
        }

        @Override
        public List<Object> getListeners() {
            return ImmutableList.of(this);
        }
    }

    private class ForwardingBuildEventConsumer extends AbstractListener<DefaultTaskFinishedProgressEvent> implements BuildEventConsumer {
        private final Provider<? extends OperationCompletionListener> listenerProvider;
        private final ImmutableList<Object> listeners;

        public ForwardingBuildEventConsumer(
            @Nullable UserCodeSource registrationPoint,
            Provider<? extends OperationCompletionListener> listenerProvider,
            ExecutorFactory executorFactory
        ) {
            super(registrationPoint, executorFactory);
            this.listenerProvider = listenerProvider;
            BuildEventSubscriptions eventSubscriptions = new BuildEventSubscriptions(Collections.singleton(OperationType.TASK));
            // TODO - share these listeners here and with the tooling api client, where possible
            listeners = ImmutableList.copyOf(factory.createListeners(eventSubscriptions, this));
        }

        @Override
        public void dispatch(Object message) {
            if (message instanceof DefaultTaskFinishedProgressEvent) {
                queue((DefaultTaskFinishedProgressEvent) message);
            }
        }

        @Override
        protected void handle(DefaultTaskFinishedProgressEvent providerEvent) {
            // TODO - reuse adapters from tooling api client
            InternalTaskDescriptor providerDescriptor = providerEvent.getDescriptor();
            InternalTaskResult providerResult = providerEvent.getResult();
            DefaultTaskOperationDescriptor descriptor = new DefaultTaskOperationDescriptor(providerDescriptor, null, providerDescriptor.getTaskPath());
            TaskOperationResult result = BuildProgressListenerAdapter.toTaskResult(providerResult);
            DefaultTaskFinishEvent finishEvent = new DefaultTaskFinishEvent(providerEvent.getEventTime(), providerEvent.getDisplayName(), descriptor, result);
            listenerProvider.get().onFinish(finishEvent);
        }

        @Override
        public List<Object> getListeners() {
            return listeners;
        }
    }

    private class ListenerCleanup extends BuildAdapter {
        @SuppressWarnings("deprecation")
        @Override
        public void buildFinished(BuildResult result) {
            // TODO - maybe make the registry a build scoped service
            if (!((GradleInternal) result.getGradle()).isRootBuild()) {
                // Stop only when the root build completes
                return;
            }
            unsubscribeAll();
        }
    }
}
