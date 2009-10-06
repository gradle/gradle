package org.gradle.api.testing.execution.fork.policies.local.single;
/*
 * Copyright 2007-2009 the original author or authors.
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

import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;

/**
 * @author Tom Eyckmans
 */
public class LocalSimpleForkPolicyForkInfo implements ForkPolicyForkInfo {

    private final ForkPolicyInstance policyInstance;
    private ExecHandleBuilder forkHandleBuilder;
    private ExecHandle forkHandle;

    public LocalSimpleForkPolicyForkInfo(ForkPolicyInstance policyInstance) {
        this.policyInstance = policyInstance;
    }

    public ExecHandleBuilder getForkHandleBuilder() {
        return forkHandleBuilder;
    }

    public void setForkHandleBuilder(ExecHandleBuilder forkHandleBuilder) {
        this.forkHandleBuilder = forkHandleBuilder;
    }

    public ExecHandle getForkHandle() {
        return forkHandle;
    }

    public void setForkHandle(ExecHandle forkHandle) {
        this.forkHandle = forkHandle;
    }

    public ForkPolicyInstance getPolicyInstance() {
        return policyInstance;
    }
}
