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

package org.gradle.api.internal.initialization;

import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Build.class)
public interface ScriptHandlerFactory {

    /**
     * Create a script handler tied to the given domain object context.
     */
    ScriptHandlerInternal create(
        ScriptSource scriptSource,
        ClassLoaderScope classLoaderScope,
        DomainObjectContext context
    );

    /**
     * Create a script handler for the given project.
     *
     * TODO: The distinction between this and {@link #create(ScriptSource, ClassLoaderScope, DomainObjectContext)}
     * should go away. Project scripts should have an anonymous root component identity.
     */
    ScriptHandlerInternal createProjectScriptHandler(
        ScriptSource scriptSource,
        ClassLoaderScope classLoaderScope,
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        ProjectInternal project
    );
}
