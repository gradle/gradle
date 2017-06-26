/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.bootstrap;

import org.gradle.internal.buildevents.BuildExceptionReporter;
import org.gradle.api.Action;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.internal.logging.DefaultLoggingConfiguration;
import org.gradle.internal.logging.text.StreamingStyledTextOutputFactory;

/**
 * An entry point is the point at which execution will never return from.
 * <p>
 * Its purpose is to consistently apply our completion logic of forcing the JVM
 * to exit at a certain point instead of waiting for all threads to die, and to provide
 * some consistent unhandled exception catching.
 * <p>
 * Entry points may be nested, as is the case when a foreground daemon is started.
 * <p>
 * The createCompleter() and createErrorHandler() are not really intended to be overridden
 * by subclasses as they define our entry point behaviour, but they are protected to enable
 * testing as it's difficult to test something that will call System.exit().
 */
public abstract class EntryPoint {

    /**
     * Unless the createCompleter() method is overridden, the JVM will exit before returning from this method.
     */
    public void run(String[] args) {
        RecordingExecutionListener listener = new RecordingExecutionListener();
        try {
            doAction(args, listener);
        } catch (Throwable e) {
            createErrorHandler().execute(e);
            listener.onFailure(e);
        }

        Throwable failure = listener.getFailure();
        ExecutionCompleter completer = createCompleter();
        if (failure == null) {
            completer.complete();
        } else {
            completer.completeWithFailure(failure);
        }
    }

    protected ExecutionCompleter createCompleter() {
        return new ProcessCompleter();
    }

    protected Action<Throwable> createErrorHandler() {
        return new BuildExceptionReporter(new StreamingStyledTextOutputFactory(System.err), new DefaultLoggingConfiguration(), new GradleLauncherMetaData());
    }

    protected abstract void doAction(String[] args, ExecutionListener listener);

    private static class RecordingExecutionListener implements ExecutionListener {
        private Throwable failure;

        public void onFailure(Throwable failure) {
            this.failure = failure;
        }

        public Throwable getFailure() {
            return failure;
        }
    }

}
