/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.play.internal.run.DefaultPlayRunSpec;
import org.gradle.play.internal.run.PlayApplicationDeploymentHandle;
import org.gradle.play.internal.run.PlayApplicationRunner;
import org.gradle.play.internal.run.PlayRunSpec;
import org.gradle.play.internal.toolchain.PlayToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Set;

/**
 * Task to run a Play application.
 */
@Incubating
@Deprecated
public class PlayRun extends ConventionTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayRun.class);

    private int httpPort;

    private final DirectoryProperty workingDir = getProject().getObjects().directoryProperty();

    private File applicationJar;

    private File assetsJar;

    private Set<File> assetsDirs;

    private FileCollection runtimeClasspath;

    private FileCollection changingClasspath;

    private BaseForkOptions forkOptions;

    private PlayToolProvider playToolProvider;

    /**
     * fork options for the running a Play application.
     */
    @Nested
    public BaseForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new BaseForkOptions();
        }
        return forkOptions;
    }

    @TaskAction
    public void run() {
        String deploymentId = getPath();
        DeploymentRegistry deploymentRegistry = getDeploymentRegistry();
        PlayApplicationDeploymentHandle deploymentHandle = deploymentRegistry.get(deploymentId, PlayApplicationDeploymentHandle.class);

        if (deploymentHandle == null) {
            PlayRunSpec spec = new DefaultPlayRunSpec(runtimeClasspath, changingClasspath, applicationJar, assetsJar, assetsDirs, workingDir.get().getAsFile(), getForkOptions(), getHttpPort());
            PlayApplicationRunner playApplicationRunner = playToolProvider.get(PlayApplicationRunner.class);
            deploymentHandle = deploymentRegistry.start(deploymentId, DeploymentRegistry.ChangeBehavior.BLOCK, PlayApplicationDeploymentHandle.class, spec, playApplicationRunner);

            InetSocketAddress playAppAddress = deploymentHandle.getPlayAppAddress();
            String playUrl = "http://localhost:" + playAppAddress.getPort() + "/";
            LOGGER.warn("Running Play App ({}) at {}", getPath(), playUrl);
        }
    }

    /**
     * The HTTP port listened to by the Play application.
     *
     * This port should be available.  The Play application will fail to start if the port is already in use.
     *
     * @return HTTP port
     */
    @Internal
    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    /**
     * The working directory.
     *
     * @since 4.4
     */
    @Internal
    public DirectoryProperty getWorkingDir() {
        return workingDir;
    }

    /**
     * The Play application jar to run.
     */
    @Classpath
    public File getApplicationJar() {
        return applicationJar;
    }

    public void setApplicationJar(File applicationJar) {
        this.applicationJar = applicationJar;
    }

    /**
     * The assets jar to run with the Play application.
     */
    @Classpath
    public File getAssetsJar() {
        return assetsJar;
    }

    public void setAssetsJar(File assetsJar) {
        this.assetsJar = assetsJar;
    }

    /**
     * The directories of the assets for the Play application (for live reload functionality).
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Set<File> getAssetsDirs() {
        return assetsDirs;
    }

    public void setAssetsDirs(Set<File> assetsDirs) {
        this.assetsDirs = assetsDirs;
    }

    /**
     * The runtime classpath for the Play application.
     *
     * @since 5.0
     */
    @Classpath
    public FileCollection getRuntimeClasspath() {
        return runtimeClasspath;
    }

    public void setRuntimeClasspath(FileCollection runtimeClasspath) {
        this.runtimeClasspath = runtimeClasspath;
    }

    /**
     * The changing classpath for the Play application.
     *
     * @since 5.0
     */
    @Classpath
    public FileCollection getChangingClasspath() {
        return changingClasspath;
    }

    public void setChangingClasspath(FileCollection changingClasspath) {
        this.changingClasspath = changingClasspath;
    }

    public void setPlayToolProvider(PlayToolProvider playToolProvider) {
        this.playToolProvider = playToolProvider;
    }

    @Inject
    public DeploymentRegistry getDeploymentRegistry() {
        throw new UnsupportedOperationException();
    }

}
