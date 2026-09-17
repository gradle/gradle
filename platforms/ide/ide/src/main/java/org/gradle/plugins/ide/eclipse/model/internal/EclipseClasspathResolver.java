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
package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.plugins.ide.internal.resolver.DefaultGradleApiSourcesResolver;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * Resolves the classpath of an {@link EclipseClasspath} with an explicit
 * {@link ProjectModulePathResolver}.
 *
 * <p>The resolver has to be passed in here rather than held on {@link EclipseClasspath}, because
 * that class is public API and must not expose an internal type. Both operations that resolve a
 * classpath live here so that the {@code eclipse} tasks and the tooling model builders share one
 * implementation and differ only in the resolver they supply.
 */
@NullMarked
public class EclipseClasspathResolver {

    private EclipseClasspathResolver() {
    }

    /**
     * Calculates the classpath entries of the given classpath.
     */
    public static List<ClasspathEntry> resolveEntries(EclipseClasspath classpath, ProjectModulePathResolver modulePathResolver) {
        ProjectInternal project = (ProjectInternal) classpath.getProject();
        ClasspathFactory classpathFactory = new ClasspathFactory(
            classpath,
            project.getServices().get(IdeArtifactRegistry.class),
            new DefaultGradleApiSourcesResolver(project.newDetachedResolver()),
            EclipseClassPathUtil.isInferModulePath(project),
            modulePathResolver);
        return classpathFactory.createEntries();
    }

    /**
     * Merges the calculated entries of the given classpath into the given XML model, running the
     * {@code beforeMerged} and {@code whenMerged} hooks around it.
     */
    @SuppressWarnings("unchecked")
    public static void mergeXmlClasspath(EclipseClasspath classpath, Classpath xmlClasspath, ProjectModulePathResolver modulePathResolver) {
        XmlFileContentMerger file = classpath.getFile();
        file.getBeforeMerged().execute(xmlClasspath);
        xmlClasspath.configure(resolveEntries(classpath, modulePathResolver));
        file.getWhenMerged().execute(xmlClasspath);
    }
}
