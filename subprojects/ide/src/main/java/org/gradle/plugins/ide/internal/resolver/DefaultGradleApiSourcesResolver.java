/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.transform.UnzipTransform;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.util.GFileUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultGradleApiSourcesResolver implements GradleApiSourcesResolver {

    private static final Logger LOGGER = Logging.getLogger(DefaultGradleApiSourcesResolver.class);

    private static final String GRADLE_REPO_URL = "https://services.gradle.org/";
    private static final String GRADLE_REPO_URL_OVERRIDE_VAR = "GRADLE_REPO_OVERRIDE";

    private final Project project;

    private final Attribute<String> artifactType = Attribute.of("artifactType", String.class);
    private final String zipType = "zip";
    private final String unzippedDistributionType = "unzipped-distribution";
    private final String sourceDirectory = "src-directory";

    public DefaultGradleApiSourcesResolver(Project project) {
        this.project = project;
    }

    @Override
    public File resolveGradleApiSources(boolean download) {
        GradleInstallation gradleInstallation = CurrentGradleInstallation.get();
        if (gradleInstallation == null) {
            return null;
        }
        File srcDir = gradleInstallation.getSrcDir();
        if (!srcDir.exists()) {
            if (!download) {
                return null;
            }
            try {
                File sources = downloadSources();
                GFileUtils.moveExistingDirectory(sources, srcDir);
            } catch (DefaultLenientConfiguration.ArtifactResolveException e) {
                LOGGER.warn("Could not fetch Gradle sources distribution: " + e.getCause().getMessage());
                return null;
            }
        }
        return srcDir;
    }

    private File downloadSources() {
        IvyArtifactRepository repository = addGradleSourcesRepository();
        try {
            registerTransforms();
            return transientConfigurationForSourcesDownload();
        } finally {
            project.getRepositories().remove(repository);
        }
    }

    private IvyArtifactRepository addGradleSourcesRepository() {
        return project.getRepositories().ivy(a -> {
            String repoName = repositoryNameFor(gradleVersion());
            a.setName("Gradle " + repoName);
            a.setUrl(gradleRepoUrl() + repoName);
            a.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            a.patternLayout(layout -> layout.artifact("[module]-[revision](-[classifier])(.[ext])"));
        });
    }

    private String gradleRepoUrl() {
        String repoOverride = System.getenv(GRADLE_REPO_URL_OVERRIDE_VAR);
        return repoOverride != null ? repoOverride : GRADLE_REPO_URL;
    }

    private void registerTransforms() {
        project.getDependencies().registerTransform(UnzipTransform.class, a -> {
            a.getFrom().attribute(artifactType, zipType);
            a.getTo().attribute(artifactType, unzippedDistributionType);
        });
        project.getDependencies().registerTransform(FindGradleSources.class, a -> {
            a.getFrom().attribute(artifactType, unzippedDistributionType);
            a.getTo().attribute(artifactType, sourceDirectory);
        });
    }

    private File transientConfigurationForSourcesDownload() {
        Configuration configuration = detachedConfigurationFor(gradleSourceDependency());
        configuration.getAttributes().attribute(artifactType, sourceDirectory);
        return configuration.getSingleFile();
    }

    private Configuration detachedConfigurationFor(Dependency dependency) {
        return project.getConfigurations().detachedConfiguration(dependency);
    }

    private GradleVersion gradleVersion() {
        return GradleVersion.version(project.getGradle().getGradleVersion());
    }

    private Dependency gradleSourceDependency() {
        Map<String, String> sourceDependency = new HashMap<>();
        sourceDependency.put("group", "gradle");
        sourceDependency.put("name", "gradle");
        sourceDependency.put("version", gradleVersion().getVersion());
        sourceDependency.put("classifier", "src");
        sourceDependency.put("ext", "zip");
        return project.getDependencies().create(sourceDependency);
    }

    private static String repositoryNameFor(GradleVersion gradleVersion) {
        return gradleVersion.isSnapshot() ? "distributions-snapshots" : "distributions";
    }

}
