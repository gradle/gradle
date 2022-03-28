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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

@DisableCachingByDefault(because = "Produces no cacheable output")
public abstract class RunApplication extends DefaultTask {
    @Inject
    protected abstract DeploymentRegistry getDeploymentRegistry();
    @Inject
    protected abstract JavaExecHandleFactory getExecActionFactory();

    @Internal
    public abstract Property<DeploymentRegistry.ChangeBehavior> getChangeBehavior();
    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();
    @Input
    public abstract Property<String> getMainClassName();
    @Input
    public abstract ListProperty<String> getArguments();

    @TaskAction
    public void startApplication() {
        DeploymentRegistry registry = getDeploymentRegistry();
        JavaApplicationHandle handle = registry.get(getPath(), JavaApplicationHandle.class);
        if (handle == null) {
            JavaExecHandleBuilder builder = getExecActionFactory().newJavaExec();
            if (getJavaLauncher().isPresent()) {
                builder.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
            } else {
                builder.setExecutable(Jvm.current().getJavaExecutable());
            }
            builder.setClasspath(getClasspath());
            builder.getMainClass().set(getMainClassName());
            builder.setArgs(getArguments().get());
            registry.start(getPath(), getChangeBehavior().getOrElse(DeploymentRegistry.ChangeBehavior.RESTART), JavaApplicationHandle.class, builder);
        }
    }
}
