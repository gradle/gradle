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

import org.gradle.api.Named;

import java.util.ArrayList;
import java.util.List;

public class XcodeScheme implements Named {
    private final String name;
    private List<BuildEntry> buildEntries = new ArrayList<BuildEntry>();
    private String buildConfiguration;

    public XcodeScheme(String name) {
        this.name = name;
    }

    public List<BuildEntry> getBuildEntries() {
        return buildEntries;
    }

    public String getBuildConfiguration() {
        return buildConfiguration;
    }

    public void setBuildConfiguration(String buildConfiguration) {
        this.buildConfiguration = buildConfiguration;
    }

    @Override
    public String getName() {
        return name;
    }

    public static class BuildEntry {
        private final XcodeTarget target;

        public BuildEntry(XcodeTarget target) {
            this.target = target;
        }

        public XcodeTarget getTarget() {
            return target;
        }
    }
}
