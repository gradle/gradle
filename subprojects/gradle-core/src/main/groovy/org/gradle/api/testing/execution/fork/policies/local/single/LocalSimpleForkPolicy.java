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
package org.gradle.api.testing.execution.fork.policies.local.single;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.control.server.ExternalControlServerFactory;
import org.gradle.api.testing.execution.control.server.TestServersManager;
import org.gradle.api.testing.execution.control.server.TestServerClientHandleFactory;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.policies.*;

/**
 * @author Tom Eyckmans
 */
public class LocalSimpleForkPolicy implements ForkPolicy {

    private final ExternalControlServerFactory testServerFactory;
    private final TestServersManager testServersManager;

    public LocalSimpleForkPolicy() {
        testServerFactory = new ExternalControlServerFactory();
        testServersManager = new TestServersManager(testServerFactory);
    }

    public ForkPolicyName getName() {
        return ForkPolicyNames.LOCAL_SIMPLE;
    }

    public ForkPolicyConfig getForkPolicyConfigInstance() {
        return new LocalSimpleForkPolicyConfig(getName());
    }

    public ForkPolicyInstance getForkPolicyInstance(Pipeline pipeline, ForkControl forkControl) {
        if (forkControl == null) throw new IllegalArgumentException("forkControl is null!");

        return new LocalSimpleForkPolicyInstance(pipeline, forkControl, testServersManager);
    }

}
