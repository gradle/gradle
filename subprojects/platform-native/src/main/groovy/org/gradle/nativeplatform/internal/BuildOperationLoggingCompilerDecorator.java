/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.internal;

import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;

public class BuildOperationLoggingCompilerDecorator<T extends BinaryToolSpec> implements Compiler<T> {

    private final Compiler<? super T> delegate;

    private BuildOperationLoggingCompilerDecorator(Compiler<? super T> delegate) {
        this.delegate = delegate;
    }

    public static <T extends BinaryToolSpec> Compiler<T> wrap(Compiler<T> delegate) {
        return new BuildOperationLoggingCompilerDecorator<T>(delegate);
    }

    @Override
    public WorkResult execute(T spec) {
        spec.getOperationLogger().start();
        try {
            return delegate.execute(spec);
        } finally {
            spec.getOperationLogger().done();
        }
    }
}
