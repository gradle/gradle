/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.Incubating;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.plugins.ide.eclipse.model.EclipseWtp;
import org.gradle.plugins.ide.eclipse.model.WbResource;
import org.jspecify.annotations.NullMarked;

import java.io.File;

@NullMarked
public interface EclipseWtpComponentInternal {
    /**
     * Source directories to be transformed into wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     * <p>
     * Only source dirs that exist will be added to the wtp component file.
     * Non-existing resource directory declarations lead to errors when project is imported into Eclipse.
     *
     * @return The source directories to be transformed into wb-resource elements.
     * @since 9.0
     */
    @Incubating
    SetProperty<File> getSourceDirsProperty();

    /**
     * The deploy name to be used.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @return The deploy name to be used.
     * @since 9.0
     */
    @Incubating
    Property<String> getDeployNameProperty();

    /**
     * The context path for the web application
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @since 9.0
     */
    @Incubating
    Property<String> getContextPathProperty();

    /**
     * The deploy path for libraries.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     *
     * @return The deploy path for libraries.
     * @since 9.0
     */
    @Incubating
    Property<String> getLibDeployPathProperty();

    /**
     * Additional wb-resource elements.
     * <p>
     * For examples see docs for {@link EclipseWtp}
     * <p>
     * Only resources that link to an existing directory ({@link WbResource#getSourcePath()})
     * will be added to the wtp component file.
     * The reason is that non-existing resource directory declarations
     * lead to errors when project is imported into Eclipse.
     *
     * @since 9.0
     */
    @Incubating
    ListProperty<WbResource> getResourcesProperty();
}
