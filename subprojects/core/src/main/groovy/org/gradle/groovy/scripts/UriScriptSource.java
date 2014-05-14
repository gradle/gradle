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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.resource.UriResource;
import org.gradle.internal.resource.Resource;
import org.gradle.internal.hash.HashUtil;

import java.io.File;
import java.net.URI;

/**
 * A {@link ScriptSource} which loads the script from a URI.
 */
public class UriScriptSource implements ScriptSource {
    private final Resource resource;
    private String className;

    public UriScriptSource(String description, File sourceFile) {
        resource = new UriResource(description, sourceFile);
    }

    public UriScriptSource(String description, URI sourceUri) {
        resource = new UriResource(description, sourceUri);
    }

    /**
     * Returns the class name for use for this script source.  The name is intended to be unique to support mapping
     * class names to source files even if many sources have the same file name (e.g. build.gradle).
     */
    public String getClassName() {
        if (className == null) {
            URI sourceUri = resource.getURI();
            String name = StringUtils.substringBeforeLast(StringUtils.substringAfterLast(sourceUri.toString(), "/"), ".");
            StringBuilder className = new StringBuilder(name.length());
            for (int i = 0; i < name.length(); i++) {
                char ch = name.charAt(i);
                if (Character.isJavaIdentifierPart(ch)) {
                    className.append(ch);
                } else {
                    className.append('_');
                }
            }
            if (!Character.isJavaIdentifierStart(className.charAt(0))) {
                className.insert(0, '_');
            }
            className.setLength(Math.min(className.length(), 30));
            className.append('_');
            String path = sourceUri.toString();
            className.append(HashUtil.createCompactMD5(path));

            this.className = className.toString();
        }

        return className;
    }

    public Resource getResource() {
        return resource;
    }

    public String getFileName() {
        File sourceFile = resource.getFile();
        URI sourceUri = resource.getURI();
        return sourceFile != null ? sourceFile.getPath() : sourceUri.toString();
    }

    public String getDisplayName() {
        return resource.getDisplayName();
    }
}
