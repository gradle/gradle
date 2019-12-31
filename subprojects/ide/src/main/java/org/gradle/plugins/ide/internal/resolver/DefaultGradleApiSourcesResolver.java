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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleInstallation;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultGradleApiSourcesResolver implements GradleApiSourcesResolver {

    private final ProjectInternal project;

    private final Attribute<String> artifactType = Attribute.of("artifactType", String.class);
    private final String zipType = "zip";
    private final String unzippedDistributionType = "unzipped-distribution";
    private final String sourceDirectory = "src-directory";

    public DefaultGradleApiSourcesResolver(ProjectInternal project) {
        this.project = project;
    }

    @Override
    public File resolveGradleApiSources(File artifact) {
        GradleInstallation gradleInstallation = CurrentGradleInstallation.get();
        if (gradleInstallation == null) {
            return null;
        }
        if (!gradleInstallation.getSrcDir().exists()) {
            downloadSources(gradleInstallation.getSrcDir());
        }
        return gradleInstallation.getSrcDir();
    }

    private void downloadSources(File srcDir) {
        IvyArtifactRepository repository = project.getRepositories().ivy(a -> {
            String repoName = repositoryNameFor(gradleVersion());
            a.setName("Gradle " + repoName);
            a.setUrl("https://services.gradle.org/" + repoName);
            a.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
            a.patternLayout(layout -> {
                /*if (isSnapshot(gradleVersion())) {
                    layout.ivy("/dummy"); // avoids a lookup that interferes with version listing
                }*/
                layout.artifact("[module]-[revision](-[classifier])(.[ext])");
            });
        });
        registerTransforms();
        try {
            File sources = transientConfigurationForSourcesDownload();
            GFileUtils.moveExistingDirectory(sources, srcDir);
        } finally {
            project.getRepositories().remove(repository);
        }
    }

    private void registerTransforms() {
        project.getDependencies().registerTransform(UnzipDistribution.class, a -> {
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

    private String repositoryNameFor(String gradleVersion) {
        return isSnapshot(gradleVersion) ? "distributions-snapshots" : "distributions";
    }

    private boolean isSnapshot(String gradleVersion) {
        return gradleVersion.contains("+");
    }

    private String gradleVersion() {
        return project.getGradle().getGradleVersion();
    }

    private Dependency gradleSourceDependency() {
        Map<String, String> sourceDependency = new HashMap<>();
        sourceDependency.put("group", "gradle");
        sourceDependency.put("name", "gradle");
        //dependencyVersion(gradleVersion));
        sourceDependency.put("version", "6.2-20191226230043+0000");
        sourceDependency.put("classifier", "src");
        sourceDependency.put("ext", "zip");
        return project.getDependencies().create(sourceDependency);
    }

}
