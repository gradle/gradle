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

import org.gradle.api.Action;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.BinaryToolSpec;

import java.io.File;
import java.util.List;

public abstract class AbstractCompiler<T extends BinaryToolSpec> implements Compiler<T> {
    private final CommandLineToolInvocationWorker commandLineToolInvocationWorker;
    private final ArgsTransformer<T> argsTransformer;
    private final CommandLineToolContext invocationContext;
    private final boolean useCommandFile;
    private final BuildOperationExecutor buildOperationExecutor;
    private final WorkerLeaseService workerLeaseService;

    protected AbstractCompiler(BuildOperationExecutor buildOperationExecutor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, ArgsTransformer<T> argsTransformer, boolean useCommandFile, WorkerLeaseService workerLeaseService) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.argsTransformer = argsTransformer;
        this.invocationContext = invocationContext;
        this.useCommandFile = useCommandFile;
        this.commandLineToolInvocationWorker = commandLineToolInvocationWorker;
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public WorkResult execute(final T spec) {
        List<String> commonArguments = getArguments(spec);
        final Action<BuildOperationQueue<CommandLineToolInvocation>> invocationAction = newInvocationAction(spec, commonArguments);

        workerLeaseService.withoutProjectLock(new Runnable() {
            @Override
            public void run() {
                buildOperationExecutor.runAll(commandLineToolInvocationWorker, invocationAction);
            }
        });

        return WorkResults.didWork(true);
    }

    // TODO(daniel): Should support in a better way multi file invocation.
    // Override this method to have multi file invocation
    protected abstract Action<BuildOperationQueue<CommandLineToolInvocation>> newInvocationAction(T spec, List<String> commonArguments);

    protected List<String> getArguments(T spec) {
        List<String> args = argsTransformer.transform(spec);

        Action<List<String>> userArgTransformer = invocationContext.getArgAction();
        // modifies in place
        userArgTransformer.execute(args);

        if (useCommandFile) {
            // Shorten args and write out an options.txt file
            // This must be called only once per execute()
            addOptionsFileArgs(args, spec.getTempDir());
        }
        return args;
    }

    protected abstract void addOptionsFileArgs(List<String> args, File tempDir);

    protected CommandLineToolInvocation newInvocation(String name, File workingDirectory, Iterable<String> args, BuildOperationLogger operationLogger) {
        return invocationContext.createInvocation(name, workingDirectory, args, operationLogger);
    }

    protected CommandLineToolInvocation newInvocation(String name, Iterable<String> args, BuildOperationLogger operationLogger) {
        return invocationContext.createInvocation(name, args, operationLogger);
    }
}
