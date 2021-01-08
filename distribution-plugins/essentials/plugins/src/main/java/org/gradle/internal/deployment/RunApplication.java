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

package org.gradle.internal.deployment;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.JavaExecHandleFactory;

import javax.inject.Inject;
import java.util.Collection;

public class RunApplication extends DefaultTask {
    private String mainClassName;
    private Collection<String> arguments;
    private FileCollection classpath;
    private DeploymentRegistry.ChangeBehavior changeBehavior = DeploymentRegistry.ChangeBehavior.RESTART;

    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @Input
    public Collection<String> getArguments() {
        return arguments;
    }

    public void setArguments(Collection<String> arguments) {
        this.arguments = arguments;
    }

    @Input
    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    @TaskAction
    public void startApplication() {
        DeploymentRegistry registry = getDeploymentRegistry();
        JavaApplicationHandle handle = registry.get(getPath(), JavaApplicationHandle.class);
        if (handle == null) {
            JavaExecHandleBuilder builder = getExecActionFactory().newJavaExec();
            builder.setExecutable(Jvm.current().getJavaExecutable());
            builder.setClasspath(classpath);
            builder.setMain(mainClassName);
            builder.setArgs(arguments);
            registry.start(getPath(), changeBehavior, JavaApplicationHandle.class, builder);
        }
    }

    @Inject
    protected DeploymentRegistry getDeploymentRegistry() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaExecHandleFactory getExecActionFactory() {
        throw new UnsupportedOperationException();
    }

    @Internal
    public DeploymentRegistry.ChangeBehavior getChangeBehavior() {
        return changeBehavior;
    }

    public void setChangeBehavior(DeploymentRegistry.ChangeBehavior changeBehavior) {
        this.changeBehavior = changeBehavior;
    }
}
