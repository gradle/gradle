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
package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.util.Node;
import org.gradle.internal.Cast;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Creates the .settings/org.eclipse.wst.common.component file for WTP projects.
 */
public class WtpComponent extends XmlPersistableConfigurationObject {

    private String deployName;
    private String contextPath;

    // TODO Change to Set?
    private List<WbModuleEntry> wbModuleEntries = Lists.newArrayList();

    public WtpComponent(XmlTransformer xmlTransformer) {
        super(xmlTransformer);
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultWtpComponent.xml";
    }

    public String getDeployName() {
        return deployName;
    }

    public void setDeployName(String deployName) {
        this.deployName = deployName;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public List<WbModuleEntry> getWbModuleEntries() {
        return wbModuleEntries;
    }

    public void setWbModuleEntries(List<WbModuleEntry> wbModuleEntries) {
        this.wbModuleEntries = wbModuleEntries;
    }

    public void configure(String deployName, String contextPath, List<WbModuleEntry> newEntries) {
        Iterable<WbModuleEntry> toKeep = Iterables.filter(wbModuleEntries, not(instanceOf(WbDependentModule.class)));
        this.wbModuleEntries = Lists.newArrayList(Sets.newLinkedHashSet(Iterables.concat(toKeep, newEntries)));
        if (!isNullOrEmpty(deployName)) {
            this.deployName = deployName;
        }
        if (!isNullOrEmpty(contextPath)) {
            this.contextPath = contextPath;
        }
    }

    @Override
    protected void load(Node xml) {
        Node wbModuleNode = getWbModuleNode(xml);
        deployName = (String) wbModuleNode.attribute("deploy-name");

        for (Node node : Cast.<List<Node>>uncheckedCast(wbModuleNode.children())) {
            if ("property".equals(node.name())) {
                if ("context-root".equals(node.attribute("name"))) {
                    contextPath = (String) node.attribute("value");
                } else {
                    wbModuleEntries.add(new WbProperty(node));
                }
            } else if ("wb-resource".equals(node.name())) {
                wbModuleEntries.add(new WbResource(node));
            } else if ("dependent-module".equals(node.name())) {
                wbModuleEntries.add(new WbDependentModule(node));
            }
        }
    }

    @Override
    protected void store(Node xml) {
        removeConfigurableDataFromXml();
        Node wbModuleNode = getWbModuleNode(xml);
        wbModuleNode.attributes().put("deploy-name", deployName);
        if (!isNullOrEmpty(contextPath)) {
            new WbProperty("context-root", contextPath).appendNode(wbModuleNode);
        }
        for (WbModuleEntry wbModuleEntry : wbModuleEntries) {
            wbModuleEntry.appendNode(wbModuleNode);
        }

    }

    private void removeConfigurableDataFromXml() {
        Node wbModuleNode = getWbModuleNode(getXml());
        for (String elementName : Arrays.asList("property", "wb-resource", "dependent-module")) {
            for (Node elementNode : XmlPersistableConfigurationObject.getChildren(wbModuleNode, elementName)) {
                wbModuleNode.remove(elementNode);
            }
        }
    }

    private static Node getWbModuleNode(Node xml) {
        Node wbModule = XmlPersistableConfigurationObject.findFirstChildNamed(xml, "wb-module");
        Preconditions.checkNotNull(wbModule);
        return wbModule;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        WtpComponent wtp = (WtpComponent) o;
        return Objects.equal(deployName, wtp.deployName)
            && Objects.equal(contextPath, wtp.contextPath)
            && Objects.equal(wbModuleEntries, wtp.wbModuleEntries);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(wbModuleEntries, deployName, contextPath);
    }

    @Override
    public String toString() {
        return "WtpComponent{"
            + "wbModuleEntries=" + wbModuleEntries
            + ", deployName='" + deployName + "\'"
            + ", contextPath='" + contextPath + "\'"
            + "}";
    }
}
