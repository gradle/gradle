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
package org.gradle.api.testing.execution.fork;

import org.gradle.api.testing.execution.QueueingPipeline;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.listener.ListenerBroadcast;

/**
 * @author Tom Eyckmans
 */
public class ForkInfo {
    private final int id;
    private final QueueingPipeline pipeline;
    private ForkPolicyForkInfo policyInfo;
    private boolean restarting;
    private final ListenerBroadcast<ForkInfoListener> listeners = new ListenerBroadcast<ForkInfoListener>(ForkInfoListener.class);

    public ForkInfo(int id, QueueingPipeline pipeline) {
        this.id = id;
        this.pipeline = pipeline;
    }

    public int getId() {
        return id;
    }

    public QueueingPipeline getPipeline() {
        return pipeline;
    }

    public ForkPolicyForkInfo getForkPolicyInfo() {
        return policyInfo;
    }

    public void setPolicyInfo(ForkPolicyForkInfo policyInfo) {
        this.policyInfo = policyInfo;
    }

    public boolean isRestarting() {
        return restarting;
    }

    public void setRestarting(boolean restarting) {
        this.restarting = restarting;
    }

    public void addListener(ForkInfoListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ForkInfoListener listener) {
        listeners.remove(listener);
    }

    public void starting() {
        listeners.getSource().starting(id);
    }

    public void started() {
        listeners.getSource().started(id);
    }

    public void finished() {
        listeners.getSource().stopped(id);
    }

    public void failed(final Throwable cause) {
        listeners.getSource().failed(id, cause);
    }

    public void aborted() {
        listeners.getSource().aborted(id);
    }
}
