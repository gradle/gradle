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


class WbDependentModule {
    String deployPath
    String handle

    def WbDependentModule(node) {
        this(node.@'deploy-path', node.@handle)
    }

    def WbDependentModule(String deployPath, String handle) {
        assert deployPath != null && handle != null
        this.deployPath = PathUtil.normalizePath(deployPath)
        this.handle = handle
    }

    void appendNode(Node parentNode) {
        Node node = parentNode.appendNode("dependent-module", ['deploy-path': deployPath, 'handle': handle])
        node.appendNode('dependency-type').setValue('uses')
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        WbDependentModule that = (WbDependentModule) o;

        if (deployPath != that.deployPath) { return false }
        if (handle != that.handle) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = deployPath.hashCode();
        result = 31 * result + handle.hashCode();
        return result;
    }

    public String toString() {
        return "WbDependentModule{" +
                "deployPath='" + deployPath + '\'' +
                ", handle='" + handle + '\'' +
                '}';
    }
}