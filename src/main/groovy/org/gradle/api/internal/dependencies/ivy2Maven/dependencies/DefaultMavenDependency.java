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
package org.gradle.api.internal.dependencies.ivy2Maven.dependencies;

import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.MavenDependency;
import org.gradle.api.internal.dependencies.ivy2Maven.XmlHelper;
import org.gradle.api.internal.dependencies.ivy2Maven.PomModuleDescriptorWriter;

import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultMavenDependency implements MavenDependency {
    private static final int START_INDENT = 4;

    private String groupId;
    private String artifactId;
    private String version;
    private String type;
    private String scope;
    private List<MavenExclude> mavenExcludes = new ArrayList<MavenExclude>();
    private boolean optional;
    private String classifier;

    public DefaultMavenDependency(String groupId, String artifactId, String version, String type, String scope,
                                  List<MavenExclude> mavenExcludes, boolean optional, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        this.scope = scope;
        this.mavenExcludes = mavenExcludes;
        this.optional = optional;
        this.classifier = classifier;
    }

    public static DefaultMavenDependency newInstance(String groupId, String artifactId, String version, String type, String scope) {
        return new DefaultMavenDependency(groupId, artifactId, version, type, scope, new ArrayList<MavenExclude>(), false, null);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public boolean isOptional() {
        return optional;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getScope() {
        return scope;
    }

    public List<MavenExclude> getMavenExcludes() {
        return mavenExcludes;
    }

    public void write(PrintWriter writer) {
        int elementIndent = START_INDENT + PomModuleDescriptorWriter.DEFAULT_INDENT;
        writer.println(XmlHelper.openTag(START_INDENT, PomModuleDescriptorWriter.DEPENDENCY));
        writeIfNotNull(writer, elementIndent, PomModuleDescriptorWriter.GROUP_ID, groupId);
        writeIfNotNull(writer, elementIndent, PomModuleDescriptorWriter.ARTIFACT_ID, artifactId);
        writeIfNotNull(writer, elementIndent, PomModuleDescriptorWriter.VERSION, version);
        writeIfNotNull(writer, elementIndent, PomModuleDescriptorWriter.SCOPE, scope);
        writeIfNotNull(writer, elementIndent, PomModuleDescriptorWriter.TYPE, type);
        if (optional) {
            writer.println(XmlHelper.enclose(elementIndent, PomModuleDescriptorWriter.OPTIONAL, "" + optional));
        }
        writeIfNotNull(writer, elementIndent, PomModuleDescriptorWriter.CLASSIFIER, classifier);
        if (mavenExcludes.size() > 0) {
            writer.println(XmlHelper.openTag(elementIndent, PomModuleDescriptorWriter.EXCLUDES));
            for (MavenExclude mavenExclude : mavenExcludes) {
                mavenExclude.write(writer);
            }
            writer.println(XmlHelper.closeTag(elementIndent, PomModuleDescriptorWriter.EXCLUDES));
        }
        writer.println(XmlHelper.closeTag(START_INDENT, PomModuleDescriptorWriter.DEPENDENCY));
    }

    private void writeIfNotNull(PrintWriter writer, int elementIndent, String elementName, String elementValue) {
        if (elementValue != null) {
            writer.println(XmlHelper.enclose(elementIndent, elementName, elementValue));
        }
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultMavenDependency that = (DefaultMavenDependency) o;

        if (artifactId != null ? !artifactId.equals(that.artifactId) : that.artifactId != null) return false;
        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) return false;
        if (groupId != null ? !groupId.equals(that.groupId) : that.groupId != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (groupId != null ? groupId.hashCode() : 0);
        result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        return result;
    }
}
