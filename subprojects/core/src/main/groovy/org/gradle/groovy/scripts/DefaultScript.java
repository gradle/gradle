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

package org.gradle.groovy.scripts;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.PathValidation;
import org.gradle.api.Script;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.ProcessOperations;
import org.gradle.api.internal.file.DefaultFileOperations;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;
import org.gradle.process.JavaExecSpec;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.net.URI;
import java.util.Map;

public abstract class DefaultScript extends BasicScript {
    private static final Logger LOGGER = Logging.getLogger(Script.class);

    private FileOperations fileOperations;
    private ProcessOperations processOperations;
    private LoggingManager loggingManager;

    public static final String SCRIPT_SERVICES_PROPERTY = "__scriptServices";
    public ServiceRegistry __scriptServices;

    public void init(final Object target, ServiceRegistry services) {
        super.init(target, services);
        this.__scriptServices = services;
        loggingManager = services.get(LoggingManager.class);
        Instantiator instantiator = services.get(Instantiator.class);
        FileLookup fileLookup = services.get(FileLookup.class);
        if (target instanceof FileOperations) {
            fileOperations = (FileOperations) target;
        } else if (getScriptSource().getResource().getFile() != null) {
            fileOperations = new DefaultFileOperations(fileLookup.getFileResolver(getScriptSource().getResource().getFile().getParentFile()), null, null, instantiator, fileLookup);
        } else {
            fileOperations = new DefaultFileOperations(fileLookup.getFileResolver(), null, null, instantiator, fileLookup);
        }

        processOperations = (ProcessOperations) fileOperations;
    }

    public FileResolver getFileResolver() {
        return fileOperations.getFileResolver();
    }

    private DefaultObjectConfigurationAction createObjectConfigurationAction() {
        ClassLoaderScope classLoaderScope = __scriptServices.get(ClassLoaderScope.class);
        return new DefaultObjectConfigurationAction(
                getFileResolver(), __scriptServices.get(ScriptPluginFactory.class), __scriptServices.get(ScriptHandlerFactory.class), classLoaderScope, getScriptTarget()
        );
    }

    public void apply(Closure closure) {
        DefaultObjectConfigurationAction action = createObjectConfigurationAction();
        ConfigureUtil.configure(closure, action);
        action.execute();
    }

    public void apply(Map options) {
        DefaultObjectConfigurationAction action = createObjectConfigurationAction();
        ConfigureUtil.configureByMap(options, action);
        action.execute();
    }

    public ScriptHandler getBuildscript() {
        return __scriptServices.get(ScriptHandler.class);
    }

    public void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript());
    }

    public File file(Object path) {
        return fileOperations.file(path);
    }

    public File file(Object path, PathValidation validation) {
        return fileOperations.file(path, validation);
    }

    public URI uri(Object path) {
        return fileOperations.uri(path);
    }

    public ConfigurableFileCollection files(Object... paths) {
        return fileOperations.files(paths);
    }

    public ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, fileOperations.files(paths));
    }

    public String relativePath(Object path) {
        return fileOperations.relativePath(path);
    }

    public ConfigurableFileTree fileTree(Object baseDir) {
        return fileOperations.fileTree(baseDir);
    }

    public ConfigurableFileTree fileTree(Map<String, ?> args) {
        return fileOperations.fileTree(args);
    }

    public ConfigurableFileTree fileTree(Object baseDir, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, fileOperations.fileTree(baseDir));
    }

    public FileTree zipTree(Object zipPath) {
        return fileOperations.zipTree(zipPath);
    }

    public FileTree tarTree(Object tarPath) {
        return fileOperations.tarTree(tarPath);
    }

    public ResourceHandler getResources() {
        return fileOperations.getResources();
    }

    public WorkResult copy(Closure closure) {
        return copy(new ClosureBackedAction<CopySpec>(closure));
    }

    public WorkResult copy(Action<? super CopySpec> action) {
        return fileOperations.copy(action);
    }

    public WorkResult sync(Action<? super CopySpec> action) {
        return fileOperations.sync(action);
    }

    public CopySpec copySpec(Closure closure) {
        return fileOperations.copySpec(new ClosureBackedAction<CopySpec>(closure));
    }

    public CopySpec copySpec(Action<? super CopySpec> action) {
        return fileOperations.copySpec(action);
    }

    public File mkdir(Object path) {
        return fileOperations.mkdir(path);
    }

    public boolean delete(Object... paths) {
        return fileOperations.delete(paths);
    }

    public ExecResult javaexec(Closure closure) {
        return processOperations.javaexec(new ClosureBackedAction<JavaExecSpec>(closure));
    }

    public ExecResult javaexec(Action<? super JavaExecSpec> action) {
        return processOperations.javaexec(action);
    }

    public ExecResult exec(Closure closure) {
        return processOperations.exec(new ClosureBackedAction<ExecSpec>(closure));
    }

    public ExecResult exec(Action<? super ExecSpec> action) {
        return processOperations.exec(action);
    }

    public LoggingManager getLogging() {
        return loggingManager;
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public String toString() {
        return "script";
    }


}
