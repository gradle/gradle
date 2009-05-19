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

/**
 * @author Hans Dockter
 */
public class Compression {
    public static final Compression NONE = new Compression("", "none");
    public static final Compression GZIP = new Compression(".gz", "gzip");
    public static final Compression BZIP2 = new Compression(".bzip2", "bzip2");

    private final String extension;

    private final String antValue;

    private Compression(String extension, String antValue) {
        this.extension = extension;
        this.antValue = antValue;
    }

    public String getExtension() {
        return extension;
    }

    public String getAntValue() {
        return antValue;
    }
}
