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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import groovy.util.Node;
import org.gradle.plugins.ide.eclipse.model.internal.PathUtil;

import java.util.Map;

/**
 * A wtp descriptor resource entry.
 */
public class WbResource implements WbModuleEntry {
    private String deployPath;
    private String sourcePath;

    public WbResource(Node node) {
        this((String) node.attribute("deploy-path"), (String) node.attribute("source-path"));
    }

    @Deprecated
    public WbResource(Object node) {
        this((Node)node);
    }

    public WbResource(String deployPath, String sourcePath) {
        Preconditions.checkNotNull(deployPath);
        Preconditions.checkNotNull(sourcePath);
        this.deployPath = PathUtil.normalizePath(deployPath);
        this.sourcePath = PathUtil.normalizePath(sourcePath);
    }

    public String getDeployPath() {
        return deployPath;
    }

    public void setDeployPath(String deployPath) {
        this.deployPath = deployPath;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @Override
    public void appendNode(Node node) {
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("deploy-path", deployPath);
        attributes.put("source-path", sourcePath);
        node.appendNode("wb-resource", attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WbResource that = (WbResource) o;
        return Objects.equal(deployPath, that.deployPath) && Objects.equal(sourcePath, that.sourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(deployPath, sourcePath);
    }

    @Override
    public String toString() {
        return "WbResource{deployPath='" + deployPath + "', sourcePath='" + sourcePath + "'}";
    }
}
