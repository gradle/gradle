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
package org.gradle.api.internal.dependencies.maven.dependencies;

import org.gradle.api.internal.dependencies.maven.XmlHelper;
import org.gradle.api.internal.dependencies.maven.PomModuleDescriptorWriter;

import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultMavenExclude implements MavenExclude {
    private String groupId;
    private String artifactId;

    public DefaultMavenExclude(String groupId, String artifactId) {
        assert groupId != null && artifactId != null;
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void write(PrintWriter writer) {
        writer.println(XmlHelper.openTag(6, "exclude"));
        writer.println(XmlHelper.enclose(8, PomModuleDescriptorWriter.GROUP_ID, groupId));
        writer.println(XmlHelper.enclose(8, PomModuleDescriptorWriter.ARTIFACT_ID, artifactId));
        writer.println(XmlHelper.closeTag(6, "exclude"));
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultMavenExclude that = (DefaultMavenExclude) o;

        if (artifactId != null ? !artifactId.equals(that.artifactId) : that.artifactId != null) return false;
        if (groupId != null ? !groupId.equals(that.groupId) : that.groupId != null) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = (groupId != null ? groupId.hashCode() : 0);
        result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
        return result;
    }
}
