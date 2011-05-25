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
package org.gradle.api.enterprise.archives.internal

import groovy.xml.QName;

import org.gradle.api.enterprise.archives.EarModule;

/**
 * @author David Gileadi
 */
class DefaultEarModule implements EarModule {

    String path;
    String altDeployDescriptor;

    public DefaultEarModule() {
    }

    public DefaultEarModule(String path) {

        this.path = path;
    }

    public Node toXmlNode(Node parentModule, Object name) {

        def node = new Node(parentModule, name, path)
        if (altDeployDescriptor) {
            new Node(parentModule, nodeNameFor("alt-dd", name), altDeployDescriptor)
        }
        return node
    }

    protected Object nodeNameFor(name, sampleName) {

        if (sampleName instanceof QName) {
            return new QName(sampleName.namespaceURI, name)
        }
        return name
    }

    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DefaultEarModule other = (DefaultEarModule) obj;
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        return true;
    }
}
