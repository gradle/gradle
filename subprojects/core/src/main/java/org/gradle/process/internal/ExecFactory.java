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

import org.gradle.api.internal.ExternalProcessStartedListener;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Manages forking/spawning processes.
 */
@ServiceScope({Scope.Global.class, Scope.UserHome.class, Scope.BuildSession.class, Scope.Build.class, Scope.Project.class})
public interface ExecFactory extends ExecActionFactory, ExecHandleFactory, JavaExecHandleFactory, JavaForkOptionsFactory, ProcessOperations {

    /**
     * Creates a new factory for the given context. Returns a {@link Builder} for further configuration of the created instance. You must provide an Instantiator when creating the child factory from
     * the root one.
     */
    Builder forContext();

    /**
     * Builder to configure an instance of the new factory.
     */
    interface Builder {
        Builder withFileResolver(FileResolver fileResolver);

        Builder withFileCollectionFactory(FileCollectionFactory fileCollectionFactory);

        Builder withInstantiator(Instantiator instantiator);

        Builder withObjectFactory(ObjectFactory objectFactory);

        Builder withJavaModuleDetector(JavaModuleDetector javaModuleDetector);

        Builder withBuildCancellationToken(BuildCancellationToken buildCancellationToken);

        Builder withExternalProcessStartedListener(ExternalProcessStartedListener externalProcessStartedListener);

        Builder withoutExternalProcessStartedListener();

        ExecFactory build();
    }
}
