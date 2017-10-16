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

package org.gradle.ide.xcode.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.ide.xcode.XcodeProject;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultXcodeProject implements XcodeProject {
    public static final String BUILD_DEBUG = "Debug";
    public static final String BUILD_RELEASE = "Release";
    public static final String TEST_DEBUG = "__GradleTestRunner_Debug";

    private final Groups groups;
    private List<XcodeTarget> targets = new ArrayList<XcodeTarget>();
    private File locationDir;

    @Inject
    public DefaultXcodeProject(ObjectFactory objectFactory) {
        this.groups = objectFactory.newInstance(Groups.class);
    }

    public Groups getGroups() {
        return groups;
    }

    public List<XcodeTarget> getTargets() {
        return targets;
    }

    public void addTarget(XcodeTarget target) {
        targets.add(target);
    }

    public File getLocationDir() {
        return locationDir;
    }

    public void setLocationDir(File locationDir) {
        this.locationDir = locationDir;
    }

    public static class Groups {
        private final ConfigurableFileCollection sources;
        private final ConfigurableFileCollection tests;
        private final ConfigurableFileCollection headers;
        private final ConfigurableFileCollection root;

        @Inject
        public Groups(FileOperations fileOperations) {
            this.sources = fileOperations.files();
            this.tests = fileOperations.files();
            this.headers = fileOperations.files();
            this.root = fileOperations.files();
        }

        public ConfigurableFileCollection getRoot() {
            return root;
        }

        public ConfigurableFileCollection getSources() {
            return sources;
        }

        public ConfigurableFileCollection getTests() {
            return tests;
        }

        public ConfigurableFileCollection getHeaders() {
            return headers;
        }
    }
}
