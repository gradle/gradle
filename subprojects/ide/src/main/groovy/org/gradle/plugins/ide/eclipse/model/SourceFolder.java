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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import groovy.util.Node;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SourceFolder.path contains only project relative path.
 */
public class SourceFolder extends AbstractClasspathEntry {
    private String output;
    private List<String> includes;
    private List<String> excludes;
    //optional
    private File dir;
    private String name;

    public SourceFolder(Node node) {
        super(node);
        this.output = normalizePath((String) node.attribute("output"));
        this.includes = parseNodeListAttribute(node, "including");
        this.excludes = parseNodeListAttribute(node, "excluding");
    }

    private List<String> parseNodeListAttribute(Node node, String attributeName) {
        Object attribute = node.attribute(attributeName);
        if (attribute == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(((String)attribute).split("\\|"));
        }
    }

    public SourceFolder(String projectRelativePath, String output) {
        super(projectRelativePath);
        this.output = normalizePath(output);
        this.includes = Lists.newArrayList();
        this.excludes = Lists.newArrayList();
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public File getDir() {
        return dir;
    }

    public void setDir(File dir) {
        this.dir = dir;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getKind() {
        return "src";
    }

    public String getAbsolutePath() {
        return dir.getAbsolutePath();
    }

    public void trim() {
        trim(null);
    }

    public void trim(String prefix) {
        if(prefix != null) {
            name = prefix + "-" + name;
        }
        path = name;
    }

    @Override
    public void appendNode(Node node) {
        Map<String, Object> attributes = Maps.newHashMap();
        Joiner joiner = Joiner.on("|");
        attributes.put("including", joiner.join(includes));
        attributes.put("excluding", joiner.join(excludes));
        attributes.put("output", output);
        addClasspathEntry(node, attributes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        SourceFolder that = (SourceFolder) o;
        return Objects.equal(exported, that.exported)
            && Objects.equal(accessRules, that.accessRules)
            && Objects.equal(excludes, that.excludes)
            && Objects.equal(includes, that.includes)
            && Objects.equal(getNativeLibraryLocation(), that.getNativeLibraryLocation())
            && Objects.equal(output, that.output)
            && Objects.equal(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(exported, accessRules, excludes, includes, getNativeLibraryLocation(), output, path);
    }

    public String toString() {
        return "SourceFolder{path='" + path + "', dir='" + dir + "', nativeLibraryLocation='" + getNativeLibraryLocation() + "', exported=" + exported
            + ", accessRules=" + accessRules + ", output='" + output + "', excludes=" + excludes + ", includes=" + includes + "}";
    }
}
