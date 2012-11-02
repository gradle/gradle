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

package org.gradle.api.publish.ivy;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.internal.HasInternalProtocol;
import org.gradle.api.publish.Publication;

/**
 * An {@code IvyPublication} is the representation/configuration of how Gradle should publish something in Ivy format.
 *
 * An ivy publication is publishable by an {@link org.gradle.api.publish.ivy.tasks.IvyPublish} task, that specifies the {@link org.gradle.api.artifacts.repositories.IvyArtifactRepository} to publish to.
 *
 * The publication's module descriptor can be modified via the {@link #descriptor(org.gradle.api.Action)} method.
 *
 * <pre autoTested="">
 * apply plugin: "ivy-publish"
 *
 * publishing {
 *   publications {
 *     ivy {
 *       descriptor {
 *         withXml {
 *           asNode().dependencies.dependency.find { it.@org == "junit" }.@rev = "4.10"
 *         }
 *       }
 *     }
 *   }
 * }
 * </pre>
 *
 * @since 1.3
 */
@Incubating
@HasInternalProtocol
public interface IvyPublication extends Publication {

    /**
     * The module descriptor that will be published.
     *
     * @return The module descriptor that will be published.
     */
    IvyModuleDescriptor getDescriptor();

    /**
     * Configures the descriptor that will be published.
     *
     * @param action The configuration action.
     */
    void descriptor(Action<? super IvyModuleDescriptor> action);

}
