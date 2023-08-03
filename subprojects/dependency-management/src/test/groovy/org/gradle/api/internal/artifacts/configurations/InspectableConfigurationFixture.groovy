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

package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact

/**
 * A fixture containing methods useful for inspecting {@link Configuration}s and the {@link DefaultConfigurationContainer}s that create them.
 */
trait InspectableConfigurationFixture {
    /**
     * Builds a formatted representation of a {@link Configuration}.
     *
     * @param the Configuration to be inspected
     * @return a formatted representation of the Configuration
     */
    String dump(Configuration configuration) {
        StringBuilder reply = new StringBuilder()

        reply.append("\nConfiguration:")
        reply.append("  class='").append(configuration.getClass()).append("'")
        reply.append("  name='").append(configuration.getName()).append("'")
        reply.append("  hashcode='").append(configuration.hashCode()).append("'")

        if (configuration instanceof DefaultConfiguration) {
            reply.append("  role='").append(configuration.roleAtCreation).append("'")
            String roleDesc = UsageDescriber.describeUsage(configuration.isCanBeConsumed(), configuration.isCanBeResolved(), configuration.isCanBeDeclared(),
                    configuration.isDeprecatedForConsumption(), configuration.isDeprecatedForResolution(), configuration.isDeprecatedForDeclarationAgainst())
            reply.append("\nCurrent Usage:\n").append(roleDesc)
        }

        reply.append("\nLocal Dependencies:")
        if (configuration.getDependencies().size() > 0) {
            configuration.getDependencies().each { Dependency d ->
                reply.append("\n   ").append(d)
            }
        } else {
            reply.append("\n   none")
        }

        reply.append("\nLocal Artifacts:")
        if (configuration.getArtifacts().size() > 0) {
            configuration.getArtifacts().each { PublishArtifact a ->
                reply.append("\n   ").append(a)
            }
        } else {
            reply.append("\n   none")
        }

        reply.append("\nAll Dependencies:")
        if (configuration.getAllDependencies().size() > 0) {
            configuration.getAllDependencies().each { Dependency d ->
                reply.append("\n   ").append(d)
            }
        } else {
            reply.append("\n   none")
        }


        reply.append("\nAll Artifacts:")
        if (configuration.getAllArtifacts().size() > 0) {
            configuration.getAllArtifacts().each { PublishArtifact a ->
                reply.append("\n   ").append(a)
            }
        } else {
            reply.append("\n   none")
        }

        return reply.toString()
    }

    /**
     * Build a formatted representation of all {@link Configuration}s in a {@link DefaultConfigurationContainer}.
     * <p>
     * Configuration(s) being toStringed are likely derivations of {@link DefaultConfiguration}.
     *
     * @param the Configuration to be inspected
     * @return a formatted representation of all Configurations in the container
     */
    String dump(DefaultConfigurationContainer container) {
        StringBuilder reply = new StringBuilder()

        reply.append("Configuration of type: ").append(container.getTypeDisplayName())
        Collection<? extends Configuration> configs = container.getAll()
        configs.each { Configuration c ->
            reply.append("\n  ").append(c.toString())
        }

        return reply.toString()
    }
}
