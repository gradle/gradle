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

import com.google.common.collect.Lists;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.xcode.XcodeProject;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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

    public Callable<List<TaskDependency>> getTaskDependencies() {
        return new Callable<List<TaskDependency>>() {
            @Override
            public List<TaskDependency> call() throws Exception {
                List<TaskDependency> result = Lists.newArrayList();
                for (XcodeTarget xcodeTarget : getTargets()) {
                    result.addAll(xcodeTarget.getTaskDependencies());
                }
                return result;
            }
        };
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

    public static abstract class Groups {
        public abstract ConfigurableFileCollection getRoot();

        public abstract ConfigurableFileCollection getSources();

        public abstract ConfigurableFileCollection getTests();

        public abstract ConfigurableFileCollection getHeaders();
    }
}
