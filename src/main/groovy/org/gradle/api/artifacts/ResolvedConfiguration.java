/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.specs.Spec;

import java.io.File;
import java.util.Set;

public interface ResolvedConfiguration {
    /**
     * Returns whether all dependencies were successfully retrieved or not.
     */
    boolean hasError();

    /**
     * A resolve of a configuration that is not successful does not automatically throws an exception.
     * Such a exception is only thrown if the result of a resolve is accessed. You can force the throwing
     * of such an exception by calling this method.  
     *
     * @throws ResolveException
     */
    void rethrowFailure() throws ResolveException;

    /**
     * Returns the files for the specified subset of configuration dependencies.
     * 
     * @param dependencySpec The filter for the configuration dependencies.
     * @return The artifact files of the specified dependencies.
     * @throws ResolveException in case the resolve was not successful.
     */
    Set<File> getFiles(Spec<Dependency> dependencySpec);

    /**
     * Returns ResolvedDependency instances for every configuration dependency. Via those instances
     * you have access to all ResolvedDependency instances (i.e. also for the resolved transitive dependencies).  
     *
     * @return A set with ResolvedDependency instances for every configuration dependency.
     * @throws ResolveException in case the resolve was not successful.
     */
    Set<ResolvedDependency> getFirstLevelModuleDependencies();
}
