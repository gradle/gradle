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


class Output implements ClasspathEntry {
    String path

    def Output(Node node) {
        this(node.@path)
    }

    def Output(String path) {
        assert path != null
        this.path = PathUtil.normalizePath(path)
    }

    String getKind() {
        'output'
    }

    void appendNode(Node node) {
        node.appendNode('classpathentry', [kind: getKind(), path: path])
    }

    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Output output = (Output) o;

        if (path != output.path) { return false }

        return true
    }

    int hashCode() {
        return path.hashCode();
    }

    public String toString() {
        return "Output{" +
                "path='" + path + '\'' +
                '}';
    }
}