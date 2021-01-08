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

package org.gradle.process.internal;

import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.reflect.Instantiator;

/**
 * Manages forking/spawning processes.
 */
public interface ExecFactory extends ExecActionFactory, ExecHandleFactory, JavaExecHandleFactory, JavaForkOptionsFactory, ProcessOperations {
    /**
     * Creates a new factory for the given context.
     */
    ExecFactory forContext(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, ObjectFactory objectFactory);

    /**
     * Creates a new factory for the given context.
     */
    ExecFactory forContext(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector);

    /**
     * Creates a new factory for the given context.
     */
    ExecFactory forContext(FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, BuildCancellationToken buildCancellationToken, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector);
}
