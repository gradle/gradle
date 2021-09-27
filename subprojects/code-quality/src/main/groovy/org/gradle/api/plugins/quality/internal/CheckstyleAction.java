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

import org.gradle.api.AntBuilder;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;
import org.gradle.api.internal.project.ant.BasicAntBuilder;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Vector;

public abstract class CheckstyleAction implements WorkAction<CheckstyleActionParameters> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckstyleAction.class);

    @Override
    public void execute() {
        LOGGER.info("Running checkstyle with toolchain '{}'.", Jvm.current().getJavaHome().getAbsolutePath());
        AntBuilder antBuilder = new BasicAntBuilder();
        AntLoggingAdapter antLogger = new AntLoggingAdapter();
        try {
            configureAntBuilder(antBuilder, antLogger);
            Object delegate = new AntBuilderDelegate(antBuilder, Thread.currentThread().getContextClassLoader());
            ClosureBackedAction.execute(delegate, new CheckstyleAntInvoker(this, this, getParameters()));
        } finally {
            disposeBuilder(antBuilder, antLogger);
        }
    }

    protected void configureAntBuilder(AntBuilder antBuilder, AntLoggingAdapter antLogger) {
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

    protected void disposeBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            // remove build listener
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            removeBuildListener.invoke(project, antLogger);
            antBuilder.getClass().getDeclaredMethod("close").invoke(antBuilder);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    private Object getProject(Object antBuilder) throws Exception {
        return antBuilder.getClass().getMethod("getProject").invoke(antBuilder);
    }

}
