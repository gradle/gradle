/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.tasks.internal;

import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.Action;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.util.HashMap;
import java.util.Map;

public class XcodeSchemeFile extends XmlPersistableConfigurationObject {
    public XcodeSchemeFile(XmlTransformer xmlTransformer) {
        super(xmlTransformer);
    }

    public BuildAction getBuildAction() {
        return new BuildAction(getOrAppendNode(getXml(), "BuildAction"));
    }

    public TestAction getTestAction() {
        return new TestAction(getOrAppendNode(getXml(), "TestAction"));
    }

    public LaunchAction getLaunchAction() {
        return new LaunchAction(getOrAppendNode(getXml(), "LaunchAction"));
    }

    public ProfileAction getProfileAction() {
        return new ProfileAction(getOrAppendNode(getXml(), "ProfileAction"));
    }

    public ArchiveAction getArchiveAction() {
        return new ArchiveAction(getOrAppendNode(getXml(), "ArchiveAction"));
    }

    public AnalyzeAction getAnalyzeAction() {
        return new AnalyzeAction(getOrAppendNode(getXml(), "AnalyzeAction"));
    }

    private static Node getOrAppendNode(Node xml, String name) {
        NodeList nodes = (NodeList) xml.get(name);
        if (nodes.isEmpty()) {
            return xml.appendNode(name);
        }
        return (Node) nodes.get(0);
    }

    @Override
    protected String getDefaultResourceName() {
        return "default.xcscheme";
    }

    public static class BuildAction {
        private final Node xml;

        BuildAction(Node xml) {
            this.xml = xml;
        }

        public void entry(Action<BuildActionEntry> action) {
            action.execute(new BuildActionEntry(getOrAppendNode(xml, "BuildActionEntries").appendNode("BuildActionEntry")));
        }
    }

    private static final String YES = "YES";
    private static final String NO = "NO";
    private static String toYesNo(boolean value) {
        if (value) {
            return YES;
        }
        return NO;
    }

    public static class BuildActionEntry {
        private final Node xml;

        BuildActionEntry(Node xml) {
            this.xml = xml;
        }

        public void setBuildForRunning(boolean buildForRunning) {
            xml.attributes().put("buildForRunning", toYesNo(buildForRunning));
        }

        public void setBuildForTesting(boolean buildForTesting) {
            xml.attributes().put("buildForTesting", toYesNo(buildForTesting));
        }

        public void setBuildForProfiling(boolean buildForProfiling) {
            xml.attributes().put("buildForProfiling", toYesNo(buildForProfiling));
        }

        public void setBuildForArchiving(boolean buildForArchiving) {
            xml.attributes().put("buildForArchiving", toYesNo(buildForArchiving));
        }

        public void setBuildForAnalysing(boolean buildForAnalysing) {
            xml.attributes().put("buildForAnalyzing", toYesNo(buildForAnalysing));
        }

        public void setBuildableReference(BuildableReference buildableReference) {
            xml.append(buildableReference.toXml());
        }
    }

    public static class TestAction {
        private final Node xml;

        TestAction(Node xml) {
            this.xml = xml;
        }

        public void setBuildConfiguration(String buildConfiguration) {
            xml.attributes().put("buildConfiguration", buildConfiguration);
        }

        public void entry(Action<TestableEntry> action) {
            action.execute(new TestableEntry(getOrAppendNode(xml, "Testables").appendNode("TestableReference")));
        }
    }

    public static class TestableEntry {
        private final Node xml;

        TestableEntry(Node xml) {
            this.xml = xml;
        }

        public void setSkipped(boolean skipped) {
            xml.attributes().put("skipped", toYesNo(skipped));
        }

        public void setBuildableReference(BuildableReference buildableReference) {
            xml.append(buildableReference.toXml());
        }
    }

    public static class LaunchAction {
        private final Node xml;

        LaunchAction(Node xml) {
            this.xml = xml;
        }

        public void setBuildConfiguration(String buildConfiguration) {
            xml.attributes().put("buildConfiguration", buildConfiguration);
        }

        public void setBuildableProductRunnable(BuildableReference buildableReference) {
            xml.appendNode("BuildableProductRunnable").append(buildableReference.toXml());
        }

        public void setBuildableReference(BuildableReference buildableReference) {
            xml.append(buildableReference.toXml());
        }
    }

    public static class ProfileAction {
        private final Node xml;

        ProfileAction(Node xml) {
            this.xml = xml;
        }

        public void setBuildConfiguration(String buildConfiguration) {
            xml.attributes().put("buildConfiguration", buildConfiguration);
        }
    }

    public static class AnalyzeAction {
        private final Node xml;

        AnalyzeAction(Node xml) {
            this.xml = xml;
        }

        public void setBuildConfiguration(String buildConfiguration) {
            xml.attributes().put("buildConfiguration", buildConfiguration);
        }
    }

    public static class ArchiveAction {
        private final Node xml;

        ArchiveAction(Node xml) {
            this.xml = xml;
        }

        public void setBuildConfiguration(String buildConfiguration) {
            xml.attributes().put("buildConfiguration", buildConfiguration);
        }
    }

    public static class BuildableReference {
        private String containerRelativePath;
        private String buildableIdentifier;
        private String blueprintIdentifier;
        private String buildableName;
        private String blueprintName;

        public String getContainerRelativePath() {
            return containerRelativePath;
        }

        public void setContainerRelativePath(String containerRelativePath) {
            this.containerRelativePath = containerRelativePath;
        }

        public String getBuildableIdentifier() {
            return buildableIdentifier;
        }

        public void setBuildableIdentifier(String buildableIdentifier) {
            this.buildableIdentifier = buildableIdentifier;
        }

        public String getBlueprintIdentifier() {
            return blueprintIdentifier;
        }

        public void setBlueprintIdentifier(String blueprintIdentifier) {
            this.blueprintIdentifier = blueprintIdentifier;
        }

        public String getBuildableName() {
            return buildableName;
        }

        public void setBuildableName(String buildableName) {
            this.buildableName = buildableName;
        }

        public String getBlueprintName() {
            return blueprintName;
        }

        public void setBlueprintName(String blueprintName) {
            this.blueprintName = blueprintName;
        }

        public Node toXml() {
            Map<String, String> attributes = new HashMap<String, String>();
            attributes.put("BuildableIdentifier", getBuildableIdentifier());
            attributes.put("BlueprintIdentifier", getBlueprintIdentifier());
            attributes.put("BuildableName", getBuildableName());
            attributes.put("BlueprintName", getBlueprintName());
            attributes.put("ReferencedContainer", "container:" + getContainerRelativePath());
            return new Node(null, "BuildableReference", attributes);
        }
    }
}
