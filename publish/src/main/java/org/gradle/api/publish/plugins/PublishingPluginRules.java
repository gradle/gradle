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

package org.gradle.api.publish.plugins;

import org.gradle.api.Task;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;

/**
 * These bindings are only here for backwards compatibility, as some users might depend on the extensions being available in the model.
 */
class PublishingPluginRules extends RuleSource {
    @Model
    PublishingExtension publishing(ExtensionContainer extensions) {
        return extensions.getByType(PublishingExtension.class);
    }

    @Model
    ProjectPublicationRegistry projectPublicationRegistry(ServiceRegistry serviceRegistry) {
        return serviceRegistry.get(ProjectPublicationRegistry.class);
    }

    @Mutate
    void addConfiguredPublicationsToProjectPublicationRegistry(ProjectPublicationRegistry projectPublicationRegistry, PublishingExtension extension) {
        //this rule is just here to ensure backwards compatibility for builds that create publications with model rules
    }

    @Mutate
    void tasksDependOnProjectPublicationRegistry(ModelMap<Task> tasks, PublishingExtension extension) {
        //this rule is just here to ensure backwards compatibility for builds that create publications with model rules
    }
}
