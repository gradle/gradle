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

package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.workers.internal.ActionExecutionSpecFactory;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.IsolatedClassLoaderWorkerRequirement;
import org.gradle.workers.internal.IsolatedClassloaderWorkerFactory;

public class ClassloaderIsolatedCompilerWorkerExecutor extends AbstractIsolatedCompilerWorkerExecutor {
    public ClassloaderIsolatedCompilerWorkerExecutor(IsolatedClassloaderWorkerFactory delegate, ActionExecutionSpecFactory actionExecutionSpecFactory) {
        super(delegate, actionExecutionSpecFactory);
    }

    @Override
    public IsolatedClassLoaderWorkerRequirement getIsolatedWorkerRequirement(DaemonForkOptions daemonForkOptions) {
        return new IsolatedClassLoaderWorkerRequirement(daemonForkOptions.getJavaForkOptions().getWorkingDir(), daemonForkOptions.getClassLoaderStructure());
    }
}
