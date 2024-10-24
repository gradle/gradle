/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.process.ExecResult;

import java.util.concurrent.Executor;

/**
 * Use {@link ExecActionFactory} (for core code) or {@link org.gradle.process.ExecOperations} (for plugin code) instead.
 */
public class DefaultJavaExecAction extends JavaExecHandleBuilder implements JavaExecAction {
    public DefaultJavaExecAction(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        ObjectFactory objectFactory,
        Executor executor,
        BuildCancellationToken buildCancellationToken,
        TemporaryFileProvider temporaryFileProvider,
        JavaModuleDetector javaModuleDetector,
        JavaForkOptionsInternal javaOptions
    ) {
        super(fileResolver, fileCollectionFactory, objectFactory, executor, buildCancellationToken, temporaryFileProvider, javaModuleDetector, javaOptions);
    }

    @Override
    public ExecResult execute() {
        ExecHandle execHandle = build();
        ExecResult execResult = execHandle.start().waitForFinish();
        if (!getIgnoreExitValue().get()) {
            execResult.assertNormalExitValue();
        }
        return execResult;
    }
}
