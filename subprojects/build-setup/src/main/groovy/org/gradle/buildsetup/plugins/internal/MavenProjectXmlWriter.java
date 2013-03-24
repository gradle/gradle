/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal;

import org.gradle.mvn3.org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.gradle.mvn3.org.apache.maven.project.MavenProject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * by Szczepan Faber, created at: 9/14/12
 */
public class MavenProjectXmlWriter {

    //TODO this class attempts to mimic the behavior of the output of mvn help:effective-pom
    //instead of this class we should walk the maven project object model (instead of parsing the xml!)

    String toXml(Set<MavenProject> projects) {
        assert !projects.isEmpty() : "Cannot prepare the maven projects effective xml because provided projects set is empty.";

        if (projects.size() == 1) {
            return toXml(projects.iterator().next());
        }

        StringBuilder out = new StringBuilder("<projects>");
        for (MavenProject project : projects) {
            out.append(toXml(project));
        }
        return out.append("</projects>").toString();
    }

    private String toXml(MavenProject project) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new MavenXpp3Writer().write(out, project.getModel());
        } catch (IOException e) {
            throw new RuntimeException("Unable to serialize maven model to xml. Maven project: " + project, e);
        }
        return prepareXml(out.toString());
    }

    String prepareXml(String xml) {
        return xml.replaceFirst("^<\\?xml.+?\\?>", "");
    }
}
