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
package org.gradle.integtests.fixtures;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.List;
import java.util.LinkedList;

import groovy.lang.Closure;

public class GradleHandles implements MethodRule {

    private final GradleDistribution distribution;
    private final GradleDistributionExecuter executer;

    private final List<GradleHandle<? extends ForkingGradleExecuter>> createdHandles = new LinkedList<GradleHandle<? extends ForkingGradleExecuter>>();

    public GradleHandles() {
        this(new GradleDistribution());
    }

    public GradleHandles(GradleDistribution distribution) {
        this.distribution = distribution;
        this.executer = new GradleDistributionExecuter(GradleDistributionExecuter.Executer.forking, distribution);
    }

    public GradleDistributionExecuter getExecuter() {
        return this.executer;
    }

    public List<GradleHandle<? extends ForkingGradleExecuter>> getCreatedHandles() {
        return new LinkedList<GradleHandle<? extends ForkingGradleExecuter>>(createdHandles);
    }

    public GradleHandle<? extends ForkingGradleExecuter> createHandle() {
        GradleHandle<? extends ForkingGradleExecuter> handle = executer.createHandle();
        createdHandles.add(handle);
        return handle;
    }

    public GradleHandle<? extends ForkingGradleExecuter> createHandle(Closure executerConfig) {
        GradleHandle<? extends ForkingGradleExecuter> handle = createHandle();
        executerConfig.setDelegate(handle.getExecuter());
        executerConfig.call(handle.getExecuter());
        return handle;
    }

    public DaemonController getDaemonController() {
        return getExecuter().getDaemonController();
    }

    public Statement apply(final Statement base, final FrameworkMethod method, final Object target) {
        return distribution.apply(executer.apply(base, method, target), method, target);
    }

}