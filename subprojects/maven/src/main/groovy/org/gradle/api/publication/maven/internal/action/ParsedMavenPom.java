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

package org.gradle.api.publication.maven.internal.action;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

class ParsedMavenPom {
    private final Model model;

    public ParsedMavenPom(File pomFile) {
        try {
            model = parsePom(pomFile);
        } catch (Exception e) {
            throw new GradleException("Cannot read generated POM!", e);
        }
    }

    private Model parsePom(File pomFile) throws IOException, XmlPullParserException {
        FileReader reader = new FileReader(pomFile);
        try {
            return new MavenXpp3Reader().read(reader, false);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    public String getGroup() {
        return model.getGroupId();
    }

    public String getArtifactId() {
        return model.getArtifactId();
    }

    public String getVersion() {
        return model.getVersion();
    }

    public String getPackaging() {
        return model.getPackaging();
    }
}
