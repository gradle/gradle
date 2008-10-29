/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.dependencies;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Hans Dockter
 */
public class Artifact {
    public static final String DEFAULT_TYPE = "jar";

    private String name;
    private String type;
    private String extension;
    private String classifier;
    private String url;

    private List<String> confs = new ArrayList<String>();


    public Artifact() {
    }

    public Artifact(String name, String type, String extension, String classifier, String url) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.classifier = classifier;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getConfs() {
        return confs;
    }

    public void setConfs(List<String> confs) {
        this.confs = confs;
    }
}
