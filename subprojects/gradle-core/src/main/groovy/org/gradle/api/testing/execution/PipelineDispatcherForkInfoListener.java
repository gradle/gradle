/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.testing.execution;

import org.gradle.api.testing.execution.fork.ForkInfoListener;

/**
 * @author Tom Eyckmans
 */
public class PipelineDispatcherForkInfoListener implements ForkInfoListener {
    private final PipelineDispatcher pipelineDispatcher;

    public PipelineDispatcherForkInfoListener(PipelineDispatcher pipelineDispatcher) {
        this.pipelineDispatcher = pipelineDispatcher;
    }

    public void starting(int forkId) {
        pipelineDispatcher.forkStarting(forkId);
    }

    public void stopped(int forkId) {
        pipelineDispatcher.forkStopped(forkId);
    }

    public void aborted(int forkId) {
        pipelineDispatcher.forkAborted(forkId);
    }

    public void failed(int forkId, Throwable cause) {
        pipelineDispatcher.forkFailed(forkId, cause);
    }
}
