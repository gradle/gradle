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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.plugins.ide.eclipse.model.internal.PathUtil


class WbResource {
    String deployPath
    String sourcePath

    def WbResource(node) {
        this(node.@'deploy-path', node.@'source-path')
    }

    def WbResource(String deployPath, String sourcePath) {
        assert deployPath != null && sourcePath != null
        this.deployPath = PathUtil.normalizePath(deployPath)
        this.sourcePath = PathUtil.normalizePath(sourcePath)
    }

    void appendNode(Node node) {
        node.appendNode("wb-resource", ['deploy-path': deployPath, 'source-path': sourcePath])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        WbResource that = (WbResource) o;

        if (deployPath != that.deployPath) { return false }
        if (sourcePath != that.sourcePath) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = deployPath.hashCode();
        result = 31 * result + sourcePath.hashCode();
        return result;
    }

    public String toString() {
        return "WbResource{" +
                "deployPath='" + deployPath + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                '}';
    }
}