/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.enterprise;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Provides executors to run jobs in the background workers. The implementation is provided by Gradle.
 */
public interface GradleEnterprisePluginBackgroundJobExecutors {
    /**
     * Returns the executor to run user-provided background jobs in the background workers.
     * The intended use is to provide workers for the {@code buildScan.background} callbacks.
     * The jobs will be complete before {@link GradleEnterprisePluginEndOfBuildListener#buildFinished(GradleEnterprisePluginEndOfBuildListener.BuildResult)} is called.
     * <p>
     * The job may be rejected if the build is already finishing and the executor is being shut down.
     * In such a case a {@link RejectedExecutionException} is thrown from {@link Executor#execute(Runnable)}.
     * <p>
     * The job should not throw exceptions.
     * Any exceptions thrown are going to fail the build and may prevent the {@code buildFinished} callback from firing.
     * <p>
     * The build configuration inputs are not recorded for the job.
     * For example, changes to the environment variables read by the job are not going to invalidate the configuration cache.
     * The job is also allowed to freely start external processes.
     *
     * @return an instance of Executor
     */
    Executor getUserJobExecutor();

    /**
     * Returns {@code true} if the current thread is running the background job submitted to one of executors owned by this class.
     */
    boolean isInBackground();
}
