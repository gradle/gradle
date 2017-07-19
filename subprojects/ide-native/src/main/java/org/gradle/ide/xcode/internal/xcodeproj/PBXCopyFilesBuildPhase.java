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

import com.google.common.base.Preconditions;

public class PBXCopyFilesBuildPhase extends PBXBuildPhase {
    /**
     * Base path of destination folder.
     */
    private Destination dstSubfolderSpec;
    /**
     * Subdirectory under the destination folder.
     */
    private String path;

    public PBXCopyFilesBuildPhase(Destination dstSubfolderSpec, String path) {
        this.dstSubfolderSpec = Preconditions.checkNotNull(dstSubfolderSpec);
        this.path = Preconditions.checkNotNull(path);
    }

    public Destination getDstSubfolderSpec() {
        return dstSubfolderSpec;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String isa() {
        return "PBXCopyFilesBuildPhase";
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);
        s.addField("dstSubfolderSpec", dstSubfolderSpec.getValue());
        s.addField("dstPath", path);
    }

    /**
     * The prefix path, this does not use SourceTreePath and build variables but rather some sort of
     * enum.
     */
    public enum Destination {
        ABSOLUTE(0),
        WRAPPER(1),
        EXECUTABLES(6),
        RESOURCES(7),
        FRAMEWORKS(10),
        SHARED_FRAMEWORKS(11),
        SHARED_SUPPORT(12),
        PLUGINS(13),
        JAVA_RESOURCES(15),
        PRODUCTS(16),;

        private int value;

        private Destination(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
