/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.StripperSpec;

import java.io.File;
import java.util.List;

public class Stripper extends AbstractCompiler<StripperSpec> {
    public Stripper(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, WorkerLeaseService workerLeaseService) {
        super(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, new StripperArgsTransformer(), false, workerLeaseService);
    }

    @Override
    protected Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(final StripperSpec spec, List<String> args) {
        final CommandLineToolInvocation invocation = newInvocation(
            "Stripping " + spec.getOutputFile().getName(), args, spec.getOperationLogger());

        return new Action<BuildOperationQueue<CommandLineToolInvocation>>() {
            @Override
            public void execute(BuildOperationQueue<CommandLineToolInvocation> buildQueue) {
                buildQueue.setLogLocation(spec.getOperationLogger().getLogLocation());
                buildQueue.add(invocation);
            }
        };
    }

    @Override
    protected void addOptionsFileArgs(List<String> args, File tempDir) { }

    private static class StripperArgsTransformer implements ArgsTransformer<StripperSpec> {
        @Override
        public List<String> transform(StripperSpec spec) {
            List<String> args = Lists.newArrayList();
            args.addAll(spec.getArgs());
            args.add("-S");
            args.add("-o");
            args.add(spec.getOutputFile().getAbsolutePath());
            args.add(spec.getBinaryFile().getAbsolutePath());
            return args;
        }
    }
}
