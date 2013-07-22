/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.tasks.bundling;

import org.gradle.util.DeprecationLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Specifies the compression which should be applied to a TAR archive.
 */
public enum Compression {
    NONE("tar"),
    GZIP("tgz", "gz"),
    BZIP2("tbz2", "bz2");

    private final String defaultExtension;
    private final List<String> supportedExtensions = new ArrayList<String>(2);

    private Compression(String defaultExtension, String... additionalSupportedExtensions) {
        this.defaultExtension = defaultExtension;
        this.supportedExtensions.addAll(Arrays.asList(additionalSupportedExtensions));
        this.supportedExtensions.add(defaultExtension);
    }

    /**
     * <p>Returns the file extension of the type of Compression.</p>
     * @deprecated Use {@link #getDefaultExtension()} instead.
     */
    public String getExtension() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("Compression.getExtension()");
        return defaultExtension;
    }

    public String getDefaultExtension(){
        return defaultExtension;
    }

    public List<String> getSupportedExtensions(){
        return supportedExtensions;
    }
}
