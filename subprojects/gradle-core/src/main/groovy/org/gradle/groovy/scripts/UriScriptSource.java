/*
 * Copyright 2009 the original author or authors.
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.GFileUtils;
import org.gradle.util.HashUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * A {@link ScriptSource} which loads the script from a URI.
 */
public class UriScriptSource implements ScriptSource {
    private final String description;
    private final File sourceFile;
    private final URI sourceUri;
    private String className;

    public UriScriptSource(String description, File sourceFile) {
        this.description = description;
        this.sourceFile = GFileUtils.canonicalise(sourceFile);
        this.sourceUri = sourceFile.toURI();
    }

    public UriScriptSource(String description, URI sourceUri) {
        this.description = description;
        this.sourceFile = sourceUri.getScheme().equals("file") ? GFileUtils.canonicalise(new File(sourceUri.getPath()))
                : null;
        this.sourceUri = sourceUri;
    }

    public String getText() {
        try {
            InputStream inputStream = sourceUri.toURL().openStream();
            try {
                return IOUtils.toString(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (FileNotFoundException e ) {
            return "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the class name for use for this script source.  The name is intended to be unique to support mapping
     * class names to source files even if many sources have the same file name (e.g. build.gradle).
     */
    public String getClassName() {
        if (className == null) {
            String name = StringUtils.substringAfterLast(sourceUri.getPath(), "/");
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
            className.append('_');
            String path = sourceUri.toString();
            className.append(HashUtil.createHash(path));

            this.className = className.toString();
        }

        return className;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public String getFileName() {
        return sourceFile.getPath();
    }

    public String getDisplayName() {
        return String.format("%s '%s'", description, sourceFile != null ? sourceFile.getAbsolutePath() : sourceUri);
    }
}
