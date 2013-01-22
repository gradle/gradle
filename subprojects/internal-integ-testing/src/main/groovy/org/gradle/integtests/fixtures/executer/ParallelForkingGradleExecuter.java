/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

import java.util.ArrayList;
import java.util.List;

class ParallelForkingGradleExecuter extends ForkingGradleExecuter {
    public ParallelForkingGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    @Override
    protected List<String> getAllArgs() {
        List<String> args = new ArrayList<String>();
        args.addAll(super.getAllArgs());
        args.add("--parallel-threads=4");
        return args;
    }

    @Override
    protected ForkingGradleHandle createGradleHandle(Action<ExecutionResult> resultAssertion, String encoding, Factory<ExecHandleBuilder> execHandleFactory) {
        return new ParallelForkingGradleHandle(resultAssertion, encoding, execHandleFactory);
    }
}
