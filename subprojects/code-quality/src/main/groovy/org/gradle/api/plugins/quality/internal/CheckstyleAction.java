/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.quality.internal;

import com.google.common.base.Suppliers;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.workers.WorkAction;

import java.lang.reflect.Method;
import java.util.Vector;
import java.util.function.Supplier;

public abstract class CheckstyleAction implements WorkAction<CheckstyleActionParameters> {

    private static final Supplier<DefaultIsolatedAntBuilder> BUILDER = Suppliers.memoize(() -> {
        ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get());
        ClassPathRegistry registry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry));
        return new DefaultIsolatedAntBuilder(registry, new DefaultClassLoaderFactory(), moduleRegistry);
    });

    @Override
    public void execute() {
        BUILDER.get().withClasspath(getParameters().getClasspath()).execute(new CheckstyleAntInvoker(this, this, getParameters()));
    }

    private static void configureAntBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method addBuildListener = projectClass.getDeclaredMethod("addBuildListener", buildListenerClass);
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            Method getBuildListeners = projectClass.getDeclaredMethod("getBuildListeners");
            Vector listeners = (Vector) getBuildListeners.invoke(project);
            removeBuildListener.invoke(project, listeners.get(0));
            addBuildListener.invoke(project, antLogger);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    private static Object getProject(Object antBuilder) throws Exception {
        return antBuilder.getClass().getMethod("getProject").invoke(antBuilder);
    }

    private static Object newInstanceOf(String className) {
        // we must use a String literal here, otherwise using things like Foo.class.name will trigger unnecessary
        // loading of classes in the classloader of the DefaultIsolatedAntBuilder, which is not what we want.
        try {
            return Class.forName(className).getConstructor().newInstance();
        } catch (Exception e) {
            // should never happen
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
