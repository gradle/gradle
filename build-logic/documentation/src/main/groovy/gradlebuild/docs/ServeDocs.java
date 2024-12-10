/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.docs;

import gradlebuild.basics.Gradle9PropertyUpgradeSupport;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.internal.deployment.JavaApplicationHandle;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.internal.JavaExecHandleBuilder;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Arrays;

/**
 * Serves the given directory with a simple HTTP server.
 */
@DisableCachingByDefault(because = "This task starts a HTTP server and should not be cached.")
public abstract class ServeDocs extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    protected abstract DirectoryProperty getDocsDirectory();

    @Input
    protected abstract Property<Integer> getPort();

    @Nested
    protected abstract Property<JavaLauncher> getJavaLauncher();

    @TaskAction
    public void startApplication() {
        System.out.println("serving docs at http://localhost:" + getPort().get());
        DeploymentRegistry registry = getDeploymentRegistry();
        JavaApplicationHandle handle = registry.get(getPath(), JavaApplicationHandle.class);
        if (handle == null) {
            JavaExecHandleBuilder builder = getExecActionFactory().newJavaExec();
            builder.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile());
            builder.getMainModule().set("jdk.httpserver");
            Gradle9PropertyUpgradeSupport.setProperty(builder, "setStandardOutput", System.out);
            Gradle9PropertyUpgradeSupport.setProperty(builder, "setErrorOutput", System.err);
            builder.setArgs(Arrays.asList("-p", getPort().get(), "-d", getDocsDirectory().get().getAsFile().getAbsolutePath()));
            registry.start(getPath(), DeploymentRegistry.ChangeBehavior.RESTART, JavaApplicationHandle.class, builder);
        }
    }

    @Inject
    protected abstract DeploymentRegistry getDeploymentRegistry();

    @Inject
    protected abstract JavaExecHandleFactory getExecActionFactory();
}
