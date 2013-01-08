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
package org.gradle.integtests.fixtures.executer;

import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

public abstract class AbstractDelegatingGradleExecuter extends AbstractGradleExecuter {

    protected AbstractDelegatingGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    @Override
    protected ExecutionResult doRun() {
        return configureExecuter().run();
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        return configureExecuter().runWithFailure();
    }

    public DaemonRegistry getDaemonRegistry() {
        return configureExecuter().getDaemonRegistry();
    }

    public void assertCanExecute() throws AssertionError {
        configureExecuter().assertCanExecute();
    }

    @Override
    public GradleHandle doStart() {
        return configureExecuter().start();
    }

    protected abstract GradleExecuter configureExecuter();

}
