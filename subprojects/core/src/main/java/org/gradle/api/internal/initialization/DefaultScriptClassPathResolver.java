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
package org.gradle.api.internal.initialization;

import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;

import java.util.List;

@SuppressWarnings("deprecation")
public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {
    private final List<ScriptClassPathInitializer> initializers;

    public DefaultScriptClassPathResolver(List<ScriptClassPathInitializer> initializers) {
        this.initializers = initializers;
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration) {
        if (classpathConfiguration == null) {
            return ClassPath.EMPTY;
        }
        for (ScriptClassPathInitializer initializer : initializers) {
            initializer.execute(classpathConfiguration);
        }
        ArtifactView view = classpathConfiguration.getIncoming().artifactView(config -> {
            config.componentFilter(componentId -> {
                if (componentId instanceof OpaqueComponentIdentifier) {
                    DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
                    return classPathNotation != DependencyFactoryInternal.ClassPathNotation.GRADLE_API && classPathNotation != DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY;
                }
                return true;
            });
        });
        return DefaultClassPath.of(view.getFiles());
    }
}
