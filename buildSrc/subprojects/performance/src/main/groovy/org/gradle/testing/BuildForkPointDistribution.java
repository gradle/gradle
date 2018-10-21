/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.caching.http.HttpBuildCache;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import static org.apache.commons.io.FileUtils.forceMkdir;

@CacheableTask
public class BuildForkPointDistribution extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(BuildForkPointDistribution.class);

    private final DirectoryProperty forkPointDistributionHome = getProject().getObjects().directoryProperty();

    private final RegularFileProperty forkPointToolingApiJar = getProject().getObjects().fileProperty();

    private Property<String> forkPointCommitId = getProject().getObjects().property(String.class);

    private Property<String> baselineVersion = getProject().getObjects().property(String.class);

    @Input
    public Property<String> getForkPointCommitId() {
        return forkPointCommitId;
    }

    @OutputDirectory
    public DirectoryProperty getForkPointDistributionHome() {
        return forkPointDistributionHome;
    }

    @OutputFile
    public RegularFileProperty getForkPointToolingApiJar() {
        return forkPointToolingApiJar;
    }

    @Internal
    public String getBaselineVersion() {
        return baselineVersion.get();
    }

    public void determineForkPoint(String forkPointCommitId, String baseVersionOnThatCommit) {
        this.forkPointCommitId.set(forkPointCommitId);
        this.baselineVersion.set(baseVersionOnThatCommit + "-commit-" + baseVersionOnThatCommit);
        this.forkPointDistributionHome.set(getProject().getRootProject().getLayout().getBuildDirectory().dir("distributions/gradle-" + baselineVersion.get()));
        this.forkPointToolingApiJar.set(getProject().getRootProject().getLayout().getBuildDirectory().file("distributions/gradle-tooling-api-" + baselineVersion.get()));
    }

    @TaskAction
    void buildDistribution() throws Exception {
        LOGGER.quiet("Building fork point distribution for: " + forkPointCommitId);
        prepareGradleRepository();
        tryBuildDistribution(forkPointCommitId.get());
        LOGGER.quiet("Building commit " + forkPointCommitId + " succeeded, now the baseline is " + getBaselineVersion());
    }

    private void prepareGradleRepository() throws IOException {
        File cloneDir = getGradleCloneTmpDir();
        if (new File(cloneDir, ".git").isDirectory()) {
            exec(cloneDir, "git", "reset", "--hard");
            exec(cloneDir, "git", "fetch");
        } else {
            forceMkdir(cloneDir);
            exec(cloneDir.getParentFile(), "git", "clone", getProject().getRootDir().getAbsolutePath(), getGradleCloneTmpDir().getAbsolutePath(), "--no-checkout");
        }
    }

    private void exec(File workingDir, Object... command) {
        getProject().exec(spec -> {
            spec.commandLine(command);
            spec.workingDir(workingDir);
        });
    }

    private File getGradleCloneTmpDir() {
        return new File(getProject().getRootProject().getBuildDir(), "tmp/gradle-find-forkpoint");
    }


    private void tryBuildDistribution(String commit) throws Exception {
        exec(getGradleCloneTmpDir(), "git", "checkout", commit, "--force");
        exec(getGradleCloneTmpDir(), (Object[]) getBuildCommands());
    }

    private Object[] getBuildCommands() {
        String[] buildCommands = new String[]{
            "./gradlew",
            "--init-script",
            new File(getGradleCloneTmpDir(), "gradle/init-scripts/build-scan.init.gradle.kts").getAbsolutePath(),
            "clean",
            ":install",
            "-Pgradle_installPath=" + forkPointDistributionHome.getAsFile().get().getAbsolutePath(),
            ":toolingApi:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + forkPointToolingApiJar.getAsFile().get().getAbsolutePath()
        };

        return Stream.of(buildCommands, getBuildCacheParams()).flatMap(Stream::of).toArray();
    }

    private String[] getBuildCacheParams() {
        if (getProject().getGradle().getStartParameter().isBuildCacheEnabled()) {
            BuildCacheConfiguration buildCacheConf = ((GradleInternal) getProject().getGradle()).getSettings().getBuildCache();
            HttpBuildCache remoteCache = (HttpBuildCache) buildCacheConf.getRemote();
            return new String[]{
                "--build-cache",
                "-Dgradle.cache.remote.url=" + (remoteCache == null ? "" : remoteCache.getUrl()),
                "-Dgradle.cache.remote.username=" + (remoteCache == null ? "" : remoteCache.getCredentials().getUsername()),
                "-Dgradle.cache.remote.password=" + (remoteCache == null ? "" : remoteCache.getCredentials().getPassword())
            };
        } else {
            return new String[0];
        }
    }
}
