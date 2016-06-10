/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear.descriptor.internal;

import com.google.common.base.Objects;
import groovy.util.Node;
import groovy.xml.QName;
import org.apache.commons.lang.StringUtils;
import org.gradle.plugins.ear.descriptor.EarModule;

public class DefaultEarModule implements EarModule {

    private String path;
    private String altDeployDescriptor;

    public DefaultEarModule() {
    }

    public DefaultEarModule(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getAltDeployDescriptor() {
        return altDeployDescriptor;
    }

    @Override
    public void setAltDeployDescriptor(String altDeployDescriptor) {
        this.altDeployDescriptor = altDeployDescriptor;
    }

    @Override
    public Node toXmlNode(Node parentModule, Object name) {
        Node node = new Node(parentModule, name, path);
        if (StringUtils.isNotEmpty(altDeployDescriptor)) {
            new Node(parentModule, nodeNameFor("alt-dd", name), altDeployDescriptor);
        }
        return node;
    }

    protected Object nodeNameFor(String name, Object sampleName) {
        if (sampleName instanceof QName) {
            return new QName(((QName) sampleName).getNamespaceURI(), name);
        }
        return name;
    }

    @Override
    public int hashCode() {
        int result;
        result = path != null ? path.hashCode() : 0;
        result = 31 * result + (altDeployDescriptor != null ? altDeployDescriptor.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultEarModule)) {
            return false;
        }
        DefaultEarModule that = (DefaultEarModule) o;
        return Objects.equal(path, that.path) && Objects.equal(altDeployDescriptor, that.altDeployDescriptor);
    }
}
