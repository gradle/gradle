/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel;

import org.gradle.api.Named;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;


//TODO version - could be different from main artifact's version
/**
 * Describes a Gradle plugin under development.
 *
 * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
 * @since 2.14
 */
public class PluginDeclaration implements Named,
    // TODO: Shouldn't be serializable, remove the interface in Gradle 8.0.
    Serializable {
    private final String name;
    private String id;
    private String implementationClass;
    private String displayName;
    private String description;

    public PluginDeclaration(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImplementationClass() {
        return implementationClass;
    }

    public void setImplementationClass(String implementationClass) {
        this.implementationClass = implementationClass;
    }

    /**
     * Returns the display name for this plugin declaration.
     *
     * <p>The display name is used when publishing this plugin to repositories
     * that support human-readable artifact names.
     *
     * @since 4.10
     */
    @Nullable
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name for this plugin declaration.
     *
     * <p>The display name is used when publishing this plugin to repositories
     * that support human-readable artifact names.
     *
     * @since 4.10
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the description for this plugin declaration.
     *
     * <p>The description is used when publishing this plugin to repositories
     * that support providing descriptions for artifacts.
     *
     * @since 4.10
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description for this plugin declaration.
     *
     * <p>The description is used when publishing this plugin to repositories
     * that support providing descriptions for artifacts.
     *
     * @since 4.10
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PluginDeclaration) {
            PluginDeclaration other = (PluginDeclaration) obj;
            return Objects.equals(name, other.name)
                && Objects.equals(id, other.id)
                && Objects.equals(implementationClass, other.implementationClass)
                && Objects.equals(displayName, other.displayName)
                && Objects.equals(description, other.description);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, implementationClass);
    }
}
