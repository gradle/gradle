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

class ModelReportNodeBuilder extends BuilderSupport {
    private ReportNode rootNode

    @Override
    protected void setParent(Object parent, Object child) {
        parent.append(child)
    }

    @Override
    protected Object createNode(Object name) {
        def node = new ReportNode(name)
        if (name == 'root') {
            this.rootNode = node
        }
        return node
    }

    @Override
    protected Object createNode(Object name, Object value) {
        return new ReportNode(name)
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        def node = new ReportNode(name, attributes)
        return node
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        return null
    }

    public ReportNode get() {
        return rootNode.children()?.find()
    }

    static ModelReportNodeBuilder fromDsl(Closure<?> closure) {
        def report = new ModelReportNodeBuilder()
        report.root(closure)
        return report
    }
}
