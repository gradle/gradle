/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.tasks.SourceSet;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class DelegatingDependencyHandler implements JvmComponentDependencies, InvocationHandler {

    private final DependencyHandler delegate;
    private final SourceSet sourceSet;

    public DelegatingDependencyHandler(DependencyHandler delegate, SourceSet sourceSet) {
        this.delegate = delegate;
        this.sourceSet = sourceSet;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method m;
        try {
            m = findMethod(this.getClass(), method);
            return m.invoke(this, args);
        } catch (NoSuchMethodException e) {
          try {
              // method not defined on this class; invoke it on the delegate
              m = findMethod(delegate.getClass(), method);
              return m.invoke(delegate, args);
          } catch (NoSuchMethodException nsme) {
              throw new GradleException("Unexpected proxy invocation", nsme);
          }
        }
    }

    private Method findMethod(Class<?> clazz, Method method) throws NoSuchMethodException {
        return clazz.getMethod(method.getName(), method.getParameterTypes());
    }

    @Override
    public void implementation(Object dependency) {
        implementation(dependency, null);
    }

    @Override
    public void implementation(Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        addToConfiguration(sourceSet.getImplementationConfigurationName(), dependencyNotation, configuration);
    }

    // TODO need an equivalent of ConfigureUtil#configureUsing(Closure), except takes Action<T> as argument and changes to a Closure
//    public void implementation(Object dependencyNotation, @Nullable Closure configureClosure) {
//        delegate.add(sourceSet.getImplementationConfigurationName(), dependencyNotation, configureClosure);
//    }

    @Override
    public void compileOnly(Object dependencyNotation) {
        compileOnly(dependencyNotation, null);
    }

    @Override
    public void compileOnly(Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        addToConfiguration(sourceSet.getCompileOnlyConfigurationName(), dependencyNotation, configuration);
    }

    @Override
    public void runtimeOnly(Object dependencyNotation) {
        runtimeOnly(dependencyNotation, null);
    }

    @Override
    public void runtimeOnly(Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        addToConfiguration(sourceSet.getRuntimeOnlyConfigurationName(), dependencyNotation, configuration);
    }

    @Override
    public void annotationProcessor(Object dependencyNotation) {
        annotationProcessor(dependencyNotation, null);
    }

    @Override
    public void annotationProcessor(Object dependencyNotation, @Nullable Action<? super Dependency> configuration) {
        addToConfiguration(sourceSet.getAnnotationProcessorConfigurationName(), dependencyNotation, configuration);
    }

    private void addToConfiguration(String configurationName, Object dependencyNotation, @Nullable Action<? super Dependency> configurationAction) {
        Dependency dep = delegate.add(configurationName, dependencyNotation);
        if (configurationAction != null) {
            configurationAction.execute(dep); // TODO nullable?
        }
    }

}
