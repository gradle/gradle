/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;

/**
 * A view over the artifacts resolved for this set of dependencies.
 *
 * By default, the view returns all files and artifacts, but this can be restricted by component identifier or by attributes.
 *
 * @since 3.4
 */
public interface ArtifactView extends HasAttributes {

    /**
     * Returns the collection of artifacts matching the requested attributes that are sourced from Components matching the specified filter.
     */
    ArtifactCollection getArtifacts();

    /**
     * Returns the collection of artifact files matching the requested attributes that are sourced from Components matching the specified filter.
     */
    FileCollection getFiles();

    /**
     * Configuration for a defined artifact view.
     *
     * @since 4.0
     */
    interface ViewConfiguration extends HasConfigurableAttributes<ViewConfiguration> {
        /**
         * Specify a filter for the components that should be included in this view.
         * Only artifacts from components matching the supplied filter will be returned by {@link #getFiles()} or {@link #getArtifacts()}.
         * This method cannot be called multiple times for a view.
         * <p>
         * A component filter can be used to select artifacts from components of a certain type:
         * <pre>{@code
         *      configuration.incoming.artifactView {
         *          componentFilter { it instanceof ProjectComponentIdentifier }
         *          // or
         *          componentFilter { it instanceof ModuleComponentIdentifier }
         *      }
         * }</pre>
         * <p>
         * Alternatively, a component filter can be used to select artifacts from specific components:
         * <pre>{@code
         *      configuration.incoming.artifactView {
         *          componentFilter {
         *              it instanceof ProjectComponentIdentifier &&
         *              it.projectPath == ":myProject"
         *          }
         *          // or
         *          componentFilter {
         *              it instanceof ModuleComponentIdentifier &&
         *                  it.group == "com.example" &&
         *                  it.module == "foo"
         *          }
         *      }
         * }</pre>
         */
        ViewConfiguration componentFilter(Spec<? super ComponentIdentifier> componentFilter);

        /**
         * Determines whether the view should be resolved in a 'lenient' fashion.
         *
         * When set to <code>true</code>, this view will resolve as many artifacts and/or files as possible
         * collecting any failures.
         *
         * When set to <code>false</code>, any failures will be propagated as exceptions when the view is resolved.
         */
        boolean isLenient();

        /**
         * Specify if the view should be resolved in a 'lenient' fashion.
         *
         * When set to <code>true</code>, this view will resolve as many artifacts and/or files as possible
         * collecting any failures.
         *
         * When set to <code>false</code>, any failures will be propagated as exceptions when the view is resolved.
         */
        void setLenient(boolean lenient);

        /**
         * Specify if the view should be resolved in a 'lenient' fashion.
         *
         * When set to <code>true</code>, this view will resolve as many artifacts and/or files as possible
         * collecting any failures.
         *
         * When set to <code>false</code>, any failures will be propagated as exceptions when the view is resolved.
         */
        ViewConfiguration lenient(boolean lenient);

        /**
         * When invoked, this view will disregard existing attributes of its parent configuration and re-resolve the artifacts
         * using only the attributes in the view's attribute container.
         *
         * <p>This behavior cannot be unset on a particular view once this method is invoked.
         *
         * @since 7.5
         */
        @Incubating
        ViewConfiguration withVariantReselection();
    }
}
