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
import org.gradle.internal.Pair;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.platform.base.internal.toolchain.ComponentFound;
import org.gradle.platform.base.internal.toolchain.ComponentNotFound;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.List;

public abstract class AbstractMetadataProvider<T extends CompilerMetadata> implements CompilerMetaDataProvider<T> {
    private final ExecActionFactory execActionFactory;

    public AbstractMetadataProvider(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    @Override
    public SearchResult<T> getCompilerMetaData(File binary, List<String> additionalArgs) {
        List<String> allArgs = ImmutableList.<String>builder().addAll(additionalArgs).addAll(compilerArgs()).build();
        Pair<String, String> transform = runCompiler(binary, allArgs);
        if (transform == null) {
            return new ComponentNotFound<T>(String.format("Could not determine %s metadata: failed to execute %s %s.", getCompilerType().getDescription(), binary.getName(), Joiner.on(' ').join(allArgs)));
        }
        String output = transform.getLeft();
        String error = transform.getRight();
        try {
            return new ComponentFound<T>(parseCompilerOutput(output, error, binary));
        } catch (BrokenResultException e) {
            return new ComponentNotFound<T>(e.getMessage());
        }
    }

    protected ExecActionFactory getExecActionFactory() {
        return execActionFactory;
    }

    protected abstract T parseCompilerOutput(String output, String error, File binary) throws BrokenResultException;

    private Pair<String, String> runCompiler(File gccBinary, List<String> args) {
        ExecAction exec = execActionFactory.newExecAction();
        exec.executable(gccBinary.getAbsolutePath());
        exec.setWorkingDir(gccBinary.getParentFile());
        exec.args(args);
        StreamByteBuffer buffer = new StreamByteBuffer();
        StreamByteBuffer errorBuffer = new StreamByteBuffer();
        exec.setStandardOutput(buffer.getOutputStream());
        exec.setErrorOutput(errorBuffer.getOutputStream());
        exec.setIgnoreExitValue(true);
        ExecResult result = exec.execute();

        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return Pair.of(buffer.readAsString(), errorBuffer.readAsString());
        } else {
            return null;
        }
    }

    protected abstract List<String> compilerArgs();

    public static class BrokenResultException extends RuntimeException {
        public BrokenResultException(String message) {
            super(message);
        }
    }

}
