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

package org.gradle.ide.xcode.internal.xcodeproj;

import java.util.ArrayList;
import java.util.List;

/**
 * Superclass of build phases. Each build phase represents one step in building a target.
 */
public abstract class PBXBuildPhase extends PBXProjectItem {
    private final List<PBXBuildFile> files;

    public PBXBuildPhase() {
        this.files = new ArrayList<>();
    }

    public List<PBXBuildFile> getFiles() {
        return files;
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        s.addField("files", files);
    }
}
