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
package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.QueueingPipeline;
import org.gradle.api.testing.execution.fork.ForkControl;

/**
 * @author Tom Eyckmans
 */
public class TestServerClientHandleFactory {
    private final ForkControl forkControl;

    public TestServerClientHandleFactory(ForkControl forkControl) {
        if (forkControl == null) {
            throw new IllegalArgumentException("forkControl == null!");
        }

        this.forkControl = forkControl;
    }

    public TestServerClientHandle createTestServerClientHandle(QueueingPipeline pipeline, int forkId) {
        return new TestServerClientHandle(pipeline, forkId, forkControl);
    }
}
