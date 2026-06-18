/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.file.PathToFileResolver;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DefaultClientExecHandleBuilderFactory implements ClientExecHandleBuilderFactory {

    private final PathToFileResolver fileResolver;
    private final ExecHandleTrackingExecutor executor;
    private final BuildCancellationToken buildCancellationToken;

    private DefaultClientExecHandleBuilderFactory(
        PathToFileResolver fileResolver,
        ExecHandleTrackingExecutor executor,
        BuildCancellationToken buildCancellationToken
    ) {
        this.fileResolver = fileResolver;
        this.executor = executor;
        this.buildCancellationToken = buildCancellationToken;
    }

    @Override
    public ClientExecHandleBuilder newExecHandleBuilder() {
        return new DefaultClientExecHandleBuilder(fileResolver, executor, buildCancellationToken)
            .listener(executor);
    }

    public static DefaultClientExecHandleBuilderFactory of(
        PathToFileResolver fileResolver,
        ExecHandleTrackingExecutor executor,
        BuildCancellationToken buildCancellationToken
    ) {
        return new DefaultClientExecHandleBuilderFactory(fileResolver, executor, buildCancellationToken);
    }
}
