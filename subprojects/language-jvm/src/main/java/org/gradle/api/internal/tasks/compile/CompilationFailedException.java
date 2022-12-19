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
package org.gradle.api.internal.tasks.compile;

import javax.annotation.Nullable;
import java.util.Optional;

public class CompilationFailedException extends RuntimeException {

    private final Object compilerPartialResult;

    public CompilationFailedException() {
        this((Object) null);
    }

    public CompilationFailedException(@Nullable Object compilerPartialResult) {
        super("Compilation failed; see the compiler error output for details.");
        this.compilerPartialResult = compilerPartialResult;
    }

    public CompilationFailedException(int exitCode) {
        super(String.format("Compilation failed with exit code %d; see the compiler error output for details.", exitCode));
        this.compilerPartialResult = null;
    }

    public CompilationFailedException(Throwable cause) {
        super(cause);
        this.compilerPartialResult = null;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getCompilerPartialResult(Class<T> type) {
        if (compilerPartialResult != null && type.isAssignableFrom(compilerPartialResult.getClass())) {
            return (Optional<T>) Optional.of(compilerPartialResult);
        }
        return Optional.empty();
    }
}
