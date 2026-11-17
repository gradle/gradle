/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Contributes project types to the {@code init} task.
 * <p>
 * The module that owns a set of project types registers an implementation as a service.
 * The {@code init} task offers every project type that the registered contributors provide.
 * <p>
 * Only Gradle's own modules can register a contributor. Plugins contribute project types through the
 * build init specs API in {@code org.gradle.buildinit.specs}.
 */
@ServiceScope(Scope.Project.class)
public interface ProjectTypeContributor {

    void contribute(Registrar registrar);

    /**
     * Receives the project types of a {@link ProjectTypeContributor}, and provides the shared services they need.
     */
    interface Registrar {

        TemplateLibraryVersionProvider getLibraryVersionProvider();

        TemplateOperationFactory getTemplateOperationFactory();

        /**
         * Registers a project type for a JVM language. The generated build also gets resource directories.
         */
        void registerJvmProjectType(LanguageSpecificProjectGenerator projectGenerator);

        /**
         * Registers a project type for a language outside the JVM.
         */
        void registerProjectType(LanguageSpecificProjectGenerator projectGenerator);
    }
}
