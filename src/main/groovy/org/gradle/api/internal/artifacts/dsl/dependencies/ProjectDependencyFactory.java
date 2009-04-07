/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.dsl.dependencies.IDependencyImplementationFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.Project;
import groovy.lang.GString;

/**
 * @author Hans Dockter
 */
public class ProjectDependencyFactory implements IDependencyImplementationFactory {
    public Dependency createDependency(Object notation) {
        assert notation != null;
        if (notation instanceof Project) {
            return new DefaultProjectDependency((Project) notation);
        }
        throw new UnknownDependencyNotation();
    }
}
