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
 * A classpath entry representing an output folder.
 */
public class Output implements ClasspathEntry {

    private String path;

    public Output(Node node) {
        this((String)node.attribute("path"));
    }

    public Output(String path) {
        Preconditions.checkNotNull(path);
        this.path = PathUtil.normalizePath(path);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getKind() {
        return "output";
    }

    @Override
    public void appendNode(Node node) {
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("kind", getKind());
        attributes.put("path", path);
        node.appendNode("classpathentry", attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Output output = (Output) o;
        return Objects.equal(path, output.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }

    @Override
    public String toString() {
        return "Output{path='" + path + "'}";
    }
}

