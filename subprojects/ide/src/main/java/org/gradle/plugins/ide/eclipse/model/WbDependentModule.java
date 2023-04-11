/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import groovy.util.Node;
import org.gradle.api.Incubating;
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil;

import java.util.Map;

/**
 * A wtp descriptor dependent module entry.
 */
public class WbDependentModule implements WbModuleEntry {

    private String archiveName;
    private String deployPath;
    private String handle;

    public WbDependentModule(Node node) {
        this((String) node.attribute("archiveName"), (String) node.attribute("deploy-path"), (String) node.attribute("handle"));
    }

    public WbDependentModule(String deployPath, String handle) {
        this("", deployPath, handle);
    }

    /**
     * Constructor for WbDependentModule
     *
     * @since 8.1
     */
    @Incubating
    public WbDependentModule(String archiveName, String deployPath, String handle) {
        Preconditions.checkNotNull(archiveName);
        Preconditions.checkNotNull(deployPath);
        this.archiveName = archiveName;
        this.deployPath = PathUtil.normalizePath(deployPath);
        this.handle = Preconditions.checkNotNull(handle);
    }

    /**
     * Get the archiveName property.
     *
     * @return the archiveName for this module.
     * @since 8.1
     */
    @Incubating
    public String getArchiveName() {
        return archiveName;
    }

    /**
     * Set the archiveName for this module.
     *
     * @param archiveName the archiveName value to set.
     * @since 8.1
     */
    @Incubating
    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
    }

    public String getDeployPath() {
        return deployPath;
    }

    public void setDeployPath(String deployPath) {
        this.deployPath = deployPath;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    @Override
    public void appendNode(Node parentNode) {
        Map<String, Object> attributes = Maps.newLinkedHashMap();
        attributes.put("archiveName", archiveName);
        attributes.put("deploy-path", deployPath);
        attributes.put("handle", handle);
        Node node = parentNode.appendNode("dependent-module", attributes);
        node.appendNode("dependency-type").setValue("uses");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        WbDependentModule that = (WbDependentModule) o;
        return Objects.equal(archiveName, that.archiveName) && Objects.equal(deployPath, that.deployPath) && Objects.equal(handle, that.handle);
    }

    @Override
    public int hashCode() {
        int result;
        result = archiveName.hashCode();
        result = 31 * result + deployPath.hashCode();
        result = 31 * result + handle.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "WbDependentModule{"
            + "archiveName='" + archiveName + "\'"
            + "deployPath='" + deployPath + "\'"
            + ", handle='" + handle + "\'"
            + "}";
    }
}
