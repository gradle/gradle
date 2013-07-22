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
package org.gradle.gradleplugin.foundation;

import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * Man! Why doesn't this get put into java's standard library?! Well, they did, but it doesn't hide hidden files. By definition of them being hidden, you probably don't want to see them. <p/> While
 * FileFilter is technically a Swing class, it shouldn't be. The foundation needs to drive what is allowed in the UI.
 */
public class ExtensionFileFilter extends FileFilter {
    private String extension;
    private String description;

    public ExtensionFileFilter(String extension, String description) {
        this.extension = extension.toLowerCase();
        this.description = description;
    }

    public boolean accept(File file) {
        if (file.isHidden()) {
            return false;
        }

        if (file.isDirectory()) {
            return true;
        }

        return file.getName().toLowerCase().endsWith(extension);
    }

    public String getDescription() {
        return description;
    }

    public String getExtension() {
        return extension;
    }
}
