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

package org.gradle.nativeplatform.toolchain.internal.metadata;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.internal.Pair;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.platform.base.internal.toolchain.ComponentFound;
import org.gradle.platform.base.internal.toolchain.ComponentNotFound;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractMetadataProvider<T extends CompilerMetadata> implements CompilerMetaDataProvider<T> {
    private final ExecActionFactory execActionFactory;

    public AbstractMetadataProvider(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    @Override
    public SearchResult<T> getCompilerMetaData(List<File> path, Action<? super CompilerExecSpec> configureAction) {
        DefaultCompilerExecSpec execSpec = new DefaultCompilerExecSpec();
        execSpec.environment("LC_MESSAGES", "C");
        configureAction.execute(execSpec);

        List<String> allArgs = ImmutableList.<String>builder().addAll(execSpec.args).addAll(compilerArgs()).build();
        Pair<String, String> transform = runCompiler(execSpec.executable, allArgs, execSpec.environments);
        if (transform == null) {
            return new ComponentNotFound<T>(String.format("Could not determine %s metadata: failed to execute %s %s.", getCompilerType().getDescription(), execSpec.executable.getName(), Joiner.on(' ').join(allArgs)));
        }
        String output = transform.getLeft();
        String error = transform.getRight();
        try {
            return new ComponentFound<T>(parseCompilerOutput(output, error, execSpec.executable, path));
        } catch (BrokenResultException e) {
            return new ComponentNotFound<T>(e.getMessage());
        }
    }

    protected ExecActionFactory getExecActionFactory() {
        return execActionFactory;
    }

    protected abstract T parseCompilerOutput(String output, String error, File binary, List<File> path) throws BrokenResultException;

    private Pair<String, String> runCompiler(File gccBinary, List<String> args, Map<String, ?> environmentVariables) {
        ExecAction exec = execActionFactory.newExecAction();
        exec.executable(gccBinary.getAbsolutePath());
        exec.setWorkingDir(gccBinary.getParentFile());
        exec.args(args);
        exec.environment(environmentVariables);
        StreamByteBuffer buffer = new StreamByteBuffer();
        StreamByteBuffer errorBuffer = new StreamByteBuffer();
        exec.getStandardOutput().set(buffer.getOutputStream());
        exec.getErrorOutput().set(errorBuffer.getOutputStream());
        exec.getIgnoreExitValue().set(true);
        ExecResult result = exec.execute();

        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return Pair.of(buffer.readAsString(), errorBuffer.readAsString());
        } else if (exitValue == 69) {
            // After an Xcode upgrade, running clang will frequently fail in a mysterious way.
            // Make the failure very obvious by throwing this back up to the user.
            String errorBufferAsString = errorBuffer.readAsString();
            if (errorBufferAsString.contains("Agreeing to the Xcode")) {
                throw new IllegalStateException("You will be unable to use Xcode's tool chain until you accept the Xcode license.\n" + errorBufferAsString);
            }
        }
        return null;
    }

    protected abstract List<String> compilerArgs();

    public static class BrokenResultException extends RuntimeException {
        public BrokenResultException(String message) {
            super(message);
        }
    }

    public static class DefaultCompilerExecSpec implements CompilerExecSpec {
        public final Map<String, String> environments = new HashMap<>();
        public final List<String> args = new ArrayList<>();
        public File executable;

        @Override
        public CompilerExecSpec environment(String key, String value) {
            environments.put(key, value);
            return this;
        }

        @Override
        public CompilerExecSpec executable(File executable) {
            this.executable = executable;
            return this;
        }

        @Override
        public CompilerExecSpec args(Iterable<String> args) {
            this.args.addAll(ImmutableList.copyOf(args));
            return this;
        }
    }

}
