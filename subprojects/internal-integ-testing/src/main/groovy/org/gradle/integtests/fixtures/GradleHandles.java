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

import groovy.lang.Closure;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GradleHandles implements MethodRule {
    private GradleDistributionExecuter executer;
    private final List<GradleHandle> createdHandles = new CopyOnWriteArrayList<GradleHandle>();

    public GradleHandle createHandle() {
        GradleHandle handle = executer.createHandle();
        createdHandles.add(handle);
        return handle;
    }

    public GradleHandle createHandle(Closure executerConfig) {
        GradleHandle handle = createHandle();
        executerConfig.setDelegate(handle.getExecuter());
        executerConfig.call(handle.getExecuter());
        return handle;
    }

    public DaemonRegistry getDaemonRegistry() {
        return executer.getDaemonRegistry();
    }

    public Statement apply(Statement base, FrameworkMethod method, final Object target) {
        executer = RuleHelper.getField(target, GradleDistributionExecuter.class);
        return base;
    }

}