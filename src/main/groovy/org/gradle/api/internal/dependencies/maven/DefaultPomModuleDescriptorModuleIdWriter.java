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
package org.gradle.api.internal.dependencies.maven;

import org.gradle.api.dependencies.maven.MavenPom;

import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorModuleIdWriter implements PomModuleDescriptorModuleIdWriter {

    public void convert(MavenPom pom, PrintWriter out) {
        assert pom != null;
        assert pom.getGroupId() != null && pom.getArtifactId() != null;
        out.println(enclose(PomModuleDescriptorWriter.GROUP_ID, pom.getGroupId()));
        out.println(enclose(PomModuleDescriptorWriter.ARTIFACT_ID, pom.getArtifactId()));
        if (pom.getVersion() != null) {
            out.println(enclose(PomModuleDescriptorWriter.VERSION, pom.getVersion()));
        }
        if (pom.getPackaging() != null) {
            out.println(enclose(PomModuleDescriptorWriter.PACKAGING, pom.getPackaging()));
        }
        if (pom.getClassifier() != null) {
            out.println(enclose(PomModuleDescriptorWriter.CLASSIFIER, pom.getClassifier()));
        }
    }

    private String enclose(String tagValue, String text) {
        return XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, tagValue, text);
    }
}
