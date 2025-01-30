/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.model
/**
 *
 * Represents a node of a hierarchical report
 * Extends from groovy's {@code groovy.util.Node} and hence GPath expressions can be used to search and identify report nodes nd values.
 *
 * See https://www.groovy-lang.org/processing-xml.html#_gpath
 *
 * E.g:
 * <pre>
 *      modelReport.reportNode.'**'.primaryCredentials.username.@nodeValue[0] == 'uname'
 *      modelReport.reportNode.'**'.primaryCredentials.username.@type[0] == 'java.lang.String'
 * </pre>
 *
 */
class ReportNode extends Node {
    Integer depth

    ReportNode(name) {
        super(null, name)
    }

    ReportNode(ReportNode parent, name) {
        super(parent, name)
    }

    ReportNode(name, Map attributes) {
        super(null, name, attributes)
    }

    ReportNode(ReportNode parent, name, Map attributes) {
        super(parent, name, attributes)
    }

    ReportNode findFirstByName(String aName) {
        return this.depthFirst().find { ReportNode node -> node.name() == aName }
    }

    String getType() {
        return attribute('type')
    }

    String getNodeValue() {
        return attribute('nodeValue')
    }

    Integer getDepth() {
        return depth
    }
}
