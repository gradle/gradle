/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.plugins;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.internal.DefaultPublicationContainer;
import org.gradle.api.publish.internal.DefaultPublishingExtension;
import org.gradle.api.publish.internal.DeferredConfigurablePublishingExtension;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.internal.model.RuleBasedPluginListener;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.DeprecationLogger;

import javax.inject.Inject;

/**
 * Installs a {@link org.gradle.api.publish.PublishingExtension} with name {@value org.gradle.api.publish.PublishingExtension#NAME}.
 *
 * @since 1.3
 */
public class PublishingPlugin implements Plugin<Project> {

    public static final String PUBLISH_TASK_GROUP = "publishing";
    public static final String PUBLISH_LIFECYCLE_TASK_NAME = "publish";

    private final Instantiator instantiator;
    private final ArtifactPublicationServices publicationServices;
    private final ProjectPublicationRegistry projectPublicationRegistry;
    private final FeaturePreviews featurePreviews;
    private final DocumentationRegistry documentationRegistry;

    @Inject
    public PublishingPlugin(ArtifactPublicationServices publicationServices, Instantiator instantiator, ProjectPublicationRegistry projectPublicationRegistry, FeaturePreviews featurePreviews, DocumentationRegistry documentationRegistry) {
        this.publicationServices = publicationServices;
        this.instantiator = instantiator;
        this.projectPublicationRegistry = projectPublicationRegistry;
        this.featurePreviews = featurePreviews;
        this.documentationRegistry = documentationRegistry;
    }

    public void apply(final Project project) {
        RepositoryHandler repositories = publicationServices.createRepositoryHandler();
        PublicationContainer publications = instantiator.newInstance(DefaultPublicationContainer.class, instantiator);
        PublishingExtension extension = project.getExtensions().create(PublishingExtension.class, PublishingExtension.NAME, determineExtensionClass(), repositories, publications);
        project.getTasks().register(PUBLISH_LIFECYCLE_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.setDescription("Publishes all publications produced by this project.");
                task.setGroup(PUBLISH_TASK_GROUP);
            }
        });
        extension.getPublications().all(new Action<Publication>() {
            @Override
            public void execute(Publication publication) {
                PublicationInternal internalPublication = (PublicationInternal) publication;
                projectPublicationRegistry.registerPublication(project.getPath(), internalPublication);
            }
        });
        bridgeToSoftwareModelIfNeeded((ProjectInternal) project);
    }

    private Class<? extends PublishingExtension> determineExtensionClass() {
        if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.STABLE_PUBLISHING)) {
            return DefaultPublishingExtension.class;
        } else {
            DeprecationLogger.nagUserWithDeprecatedBuildInvocationFeature(
                "As part of making the publishing plugins stable, the 'deferred configurable' behavior of the 'publishing {}' block",
                "In Gradle 5.0 the 'enableFeaturePreview('STABLE_PUBLISHING')' flag will be removed and the new behavior will become the default.",
                    "Please add 'enableFeaturePreview('STABLE_PUBLISHING')' to your settings file and do a test run by publishing to a local repository. " +
                    "If all artifacts are published as expected, there is nothing else to do. " +
                    "If the published artifacts change unexpectedly, please see the migration guide for more details: " + documentationRegistry.getDocumentationFor("publishing_maven", "publishing_maven:deferred_configuration") + ".");
            return DeferredConfigurablePublishingExtension.class;
        }
    }

    private void bridgeToSoftwareModelIfNeeded(ProjectInternal project) {
        if (featurePreviews.isFeatureEnabled(FeaturePreviews.Feature.STABLE_PUBLISHING)) {
            project.addRuleBasedPluginListener(new RuleBasedPluginListener() {
                @Override
                public void prepareForRuleBasedPlugins(Project project) {
                    project.getPluginManager().apply(PublishingPluginRules.class);
                }
            });
        } else {
            project.getPluginManager().apply(PublishingPluginRules.class);
        }
    }

}
