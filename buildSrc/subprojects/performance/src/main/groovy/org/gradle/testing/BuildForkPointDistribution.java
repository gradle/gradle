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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.caching.http.HttpBuildCache;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import static org.apache.commons.io.FileUtils.cleanDirectory;
import static org.apache.commons.io.FileUtils.forceMkdir;

@CacheableTask
public class BuildForkPointDistribution extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(BuildForkPointDistribution.class);

    private final DirectoryProperty forkPointDistributionDir = getProject().getObjects().directoryProperty();

    private String forkPointCommitId;

    private String baselineVersion;

    @Input
    @Optional
    public String getForkPointCommitId() {
        return forkPointCommitId;
    }

    public void setForkPointCommitId(String forkPointCommitId) {
        this.forkPointCommitId = forkPointCommitId;
        this.forkPointDistributionDir.set(getProject().getRootProject().getLayout().getBuildDirectory().dir("distributions/gradle-commit-" + forkPointCommitId));
    }

    @OutputDirectory
    public DirectoryProperty getForkPointDistributionDir() {
        return forkPointDistributionDir;
    }

    @Internal
    public String getBaselineVersion() {
        if (baselineVersion == null) {
            baselineVersion = Stream.of(getForkPointDistributionDir().getAsFile().get().listFiles())
                .filter(file -> file.isDirectory() && file.getName().startsWith("gradle-"))
                .map(file -> file.getName().substring("gradle-".length()))
                .findFirst()
                .orElse("defaults");
        }
        return baselineVersion;
    }

    @TaskAction
    void buildDistribution() throws Exception {
        LOGGER.quiet("Building fork point distribution for: " + forkPointCommitId);
        prepareGradleRepository();
        tryBuildDistribution(forkPointCommitId);
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
        cleanDirectory(getForkPointDistributionDir().getAsFile().get());

        String baseVersion = new String(Files.readAllBytes(getGradleCloneTmpDir().toPath().resolve("version.txt"))).trim();

        exec(getGradleCloneTmpDir(), (Object[]) getBuildCommands(commit, baseVersion));
    }

    private Object[] getBuildCommands(String commit, String baseVersion) {
        BuildCacheConfiguration buildCacheConf = ((GradleInternal) getProject().getGradle()).getSettings().getBuildCache();
        HttpBuildCache remoteCache = (HttpBuildCache) buildCacheConf.getRemote();
        boolean remoteCacheEnabled = remoteCache == null ? false : remoteCache.isEnabled();

        return new String[]{
            "./gradlew",
            "clean",
            ":install",
            "-Pgradle_installPath=" + getDestinationFile("gradle-" + baseVersion + "-commit-" + commit),
            ":toolingApi:installToolingApiShadedJar",
            "-PtoolingApiShadedJarInstallPath=" + getDestinationFile("gradle-tooling-api-" + baseVersion + "-commit-" + commit + ".jar"),
            "-Dorg.gradle.caching=" + (remoteCache == null ? false : remoteCache.isEnabled()),
            "-Dgradle.cache.remote.url=" + (remoteCache == null ? "" : remoteCache.getUrl()),
            "-Dgradle.cache.remote.username=" + (remoteCache == null ? "" : remoteCache.getCredentials().getUsername()),
            "-Dgradle.cache.remote.password=" + (remoteCache == null ? "" : remoteCache.getCredentials().getPassword())
        };
    }

    private String getDestinationFile(String fileName) {
        return new File(getForkPointDistributionDir().getAsFile().get(), fileName).getAbsolutePath();
    }
}
