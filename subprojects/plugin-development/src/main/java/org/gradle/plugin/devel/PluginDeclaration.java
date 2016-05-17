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

import com.google.common.base.Objects;
import org.gradle.api.Incubating;
import org.gradle.api.Named;

import java.io.Serializable;


//TODO version - could be different from main artifact's version
//TODO description - could be put in the Pom/Ivy file for clarity
/**
 * Describes a Gradle plugin under development.
 *
 * @see org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin
 * @since 2.14
 */
@Incubating
public class PluginDeclaration implements Named, Serializable {
    private final String name;
    private String id;
    private String implementationClass;

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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PluginDeclaration) {
            PluginDeclaration other = (PluginDeclaration) obj;
            return Objects.equal(name, other.name)
                && Objects.equal(id, other.id)
                && Objects.equal(implementationClass, other.implementationClass);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, id, implementationClass);
    }
}
