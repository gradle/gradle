/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JvmEcosystemPlugin;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;

public class AbstractScannerPlugin implements Plugin<Project>  {

    private Attribute<Boolean> analyzed = Attribute.of("dependency-analyzed", Boolean.class);

    private JvmPluginServices pluginServices;

    @Override
    public void apply(Project project) {

        project.getPlugins().apply(JvmEcosystemPlugin.class);

        this.pluginServices = ((ProjectInternal) project).getServices().get(JvmPluginServices.class);

        // Configure artifact transform
        project.getDependencies().getAttributesSchema().attribute(analyzed);
        project.getDependencies().registerTransform(AnalyzingArtifactTransform.class, spec -> {
            spec.getFrom().attribute(analyzed, false);
            spec.getTo().attribute(analyzed, true);
        });

        // TODO: Do we need to add this on classes too?
        // TODO: Why do we need this?
        project.getDependencies().getArtifactTypes().getByName(ArtifactTypeDefinition.JAR_TYPE).getAttributes()
            .attribute(analyzed, false);
    }

    protected void configureAsAnalysisClasspath(Configuration configuration) {
        // Resolve for runtime, since we don't have a compileElements to resolve for compile time instead.
        // Most importantly, we need the implementation/api deps. RuntimeOnly/compileOnly/compileOnlyApi are the least of our worries right now.
        pluginServices.configureAsRuntimeClasspath(configuration);

        // When we resolve this configuration, fetch the analysis for each dependency instead of the
        // dependency themselves.
        configuration.getAttributes().attribute(analyzed, true);
    }
}


