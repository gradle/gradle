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
package org.gradle.plugins.ear.descriptor.internal

import groovy.xml.QName
import org.gradle.plugins.ear.descriptor.EarModule

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

    int hashCode() {
        int result;
        result = (path != null ? path.hashCode() : 0);
        result = 31 * result + (altDeployDescriptor != null ? altDeployDescriptor.hashCode() : 0);
        return result;
    }

    boolean equals(o) {
        if (this.is(o)) { return true; }
        if (!(o instanceof DefaultEarModule)) { return false; }

        DefaultEarModule that = (DefaultEarModule) o;

        if (altDeployDescriptor != that.altDeployDescriptor) { return false; }
        if (path != that.path) { return false; }

        return true;
    }
}
