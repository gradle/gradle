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

package org.gradle.api.internal.artifacts.dsl;

import org.apache.commons.lang.StringUtils;

import java.io.File;

/**
 * Given a Module and a File that is to be an artifact, attempts to determine the appropriate name+classifier+extension from the file name.
 */
public class ArtifactFile {
    private String name;
    private String classifier;
    private String extension;

    public ArtifactFile(File file, String version) {
        this(file.getName(), version);
    }

    public ArtifactFile(String fileBaseName, String version) {
        name = fileBaseName;
        extension = "";
        classifier = "";
        boolean done = false;

        int startVersion = StringUtils.lastIndexOf(name, "-" + version);
        if (startVersion >= 0) {
            int endVersion = startVersion + version.length() + 1;
            if (endVersion == name.length()) {
                name = name.substring(0, startVersion);
                done = true;
            } else if (endVersion < name.length() && name.charAt(endVersion) == '-') {
                String tail = name.substring(endVersion + 1);
                name = name.substring(0, startVersion);
                classifier = StringUtils.substringBeforeLast(tail, ".");
                extension = StringUtils.substringAfterLast(tail, ".");
                done = true;
            } else if (endVersion < name.length() && StringUtils.lastIndexOf(name, ".") == endVersion) {
                extension = name.substring(endVersion + 1);
                name = name.substring(0, startVersion);
                done = true;
            }
        }
        if (!done) {
            extension = StringUtils.substringAfterLast(name, ".");
            name = StringUtils.substringBeforeLast(name, ".");
        }
        if (classifier.length() == 0) {
            classifier = null;
        }
    }

    public String getName() {
        return name;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getExtension() {
        return extension;
    }
}
