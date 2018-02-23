/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.groovy.scripts;

import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.internal.resource.TextResource;

import java.net.URI;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static org.apache.commons.lang.StringUtils.substringAfterLast;
import static org.apache.commons.lang.StringUtils.substringBeforeLast;

/**
 * A {@link ScriptSource} which loads the script from a URI.
 */
public class TextResourceScriptSource implements ScriptSource {
    private final TextResource resource;
    private String className;

    public TextResourceScriptSource(TextResource resource) {
        this.resource = resource;
    }

    public TextResource getResource() {
        return resource;
    }

    public String getFileName() {
        ResourceLocation location = resource.getLocation();
        if (location.getFile() != null) {
            return location.getFile().getPath();
        }
        if (location.getURI() != null) {
            return location.getURI().toString();
        }
        return getClassName();
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }

    /**
     * Returns the class name for use for this script source.  The name is intended to be unique to support mapping
     * class names to source files even if many sources have the same file name (e.g. build.gradle).
     */
    public String getClassName() {
        if (className == null) {
            this.className = initClassName();
        }
        return className;
    }

    private String initClassName() {
        URI sourceUri = getResource().getLocation().getURI();
        if (sourceUri != null) {
            String path = sourceUri.toString();
            return classNameFromPath(path);
        }

        return "script_" + HashUtil.createCompactMD5(resource.getText());
    }

    private String classNameFromPath(String path) {
        String name = substringBeforeLast(substringAfterLast(path, "/"), ".");

        StringBuilder className = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            className.append(
                isJavaIdentifierPart(ch) ? ch : '_');
        }
        if (!isJavaIdentifierStart(className.charAt(0))) {
            className.insert(0, '_');
        }
        className.setLength(Math.min(className.length(), 30));
        className.append('_');
        className.append(HashUtil.createCompactMD5(path));

        return className.toString();
    }

}
