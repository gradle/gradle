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
package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Executes a closure against an isolated {@link org.gradle.api.AntBuilder} instance.
 */
@ServiceScope(Scope.Build.class)
public interface IsolatedAntBuilder {

    /**
     * Creates a copy of this builder which uses the given libraries. These classes are visible for use in
     * taskdef/typedef tasks.
     *
     * @param classpath The library classpath
     * @return a copy of this builder
     */
    IsolatedAntBuilder withClasspath(Iterable<File> classpath);

    /**
     * Executes the given closure against an isolated {@link org.gradle.api.AntBuilder} instance. The builder will
     * have visible to it an isolated version of Ant, Groovy and the specified libraries (if any). Each call to this
     * method is given a separate Ant project.
     */
    void execute(Closure antClosure);

    void execute(Action<AntBuilderDelegate> antBuilderAction);
}
