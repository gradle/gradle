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

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.gradle.api.Named;

import javax.annotation.Nullable;

/**
 * Superclass for file, directories, and groups. Xcode's virtual file hierarchy are made of these
 * objects.
 */
public class PBXReference extends PBXContainerItem implements Named {
    private final String name;
    @Nullable
    private String path;
    /**
     * The "base" path of the reference. The absolute path is resolved by prepending the resolved
     * base path.
     */
    private SourceTree sourceTree;

    public PBXReference(String name, @Nullable String path, SourceTree sourceTree) {
        this.name = Preconditions.checkNotNull(name);
        this.path = path;
        this.sourceTree = Preconditions.checkNotNull(sourceTree);
    }

    @Override
    public String getName() {
        return name;
    }

    @Nullable
    public String getPath() {
        return path;
    }

    public void setPath(String v) {
        path = v;
    }

    public SourceTree getSourceTree() {
        return sourceTree;
    }

    public void setSourceTree(SourceTree v) {
        sourceTree = v;
    }

    @Override
    public String isa() {
        return "PBXReference";
    }

    @Override
    public int stableHash() {
        return name.hashCode();
    }

    @Override
    public void serializeInto(XcodeprojSerializer s) {
        super.serializeInto(s);

        s.addField("name", name);
        if (path != null) {
            s.addField("path", path);
        }
        s.addField("sourceTree", sourceTree.toString());
    }

    @Override
    public String toString() {
        return String.format(
            "%s name=%s path=%s sourceTree=%s",
            super.toString(),
            getName(),
            getPath(),
            getSourceTree());
    }

    public enum SourceTree {
        /**
         * Relative to the path of the group containing this.
         */
        GROUP("<group>"),

        /**
         * Absolute system path.
         */
        ABSOLUTE("<absolute>"),
        /**
         * Relative to the build setting {@code BUILT_PRODUCTS_DIR}.
         */
        BUILT_PRODUCTS_DIR("BUILT_PRODUCTS_DIR"),

        /**
         * Relative to the build setting {@code SDKROOT}.
         */
        SDKROOT("SDKROOT"),

        /**
         * Relative to the directory containing the project file {@code SOURCE_ROOT}.
         */
        SOURCE_ROOT("SOURCE_ROOT"),

        /**
         * Relative to the Developer content directory inside the Xcode application
         * (e.g. {@code /Applications/Xcode.app/Contents/Developer}).
         */
        DEVELOPER_DIR("DEVELOPER_DIR"),;

        private final String rep;

        SourceTree(String str) {
            rep = str;
        }

        /**
         * Return a sourceTree given a build setting that is typically used as a source tree prefix.
         *
         * The build setting may be optionally prefixed by '$' which will be stripped.
         */
        public static Optional<SourceTree> fromBuildSetting(String buildSetting) {
            String data = CharMatcher.is('$').trimLeadingFrom(buildSetting);
            if (data.equals("BUILT_PRODUCTS_DIR")) {
                return Optional.of(BUILT_PRODUCTS_DIR);
            } else if (data.equals("SDKROOT")) {
                return Optional.of(SDKROOT);
            } else if (data.equals("SOSURCE_ROOT")) {
                return Optional.of(SOURCE_ROOT);
            } else if (data.equals("DEVELOPER_DIR")) {
                return Optional.of(DEVELOPER_DIR);
            } else {
                return Optional.absent();
            }
        }

        @Override
        public String toString() {
            return rep;
        }
    }
}
