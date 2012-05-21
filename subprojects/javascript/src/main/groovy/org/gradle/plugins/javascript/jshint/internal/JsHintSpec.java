/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.jshint.internal;

import java.io.File;
import java.io.Serializable;

public class JsHintSpec implements Serializable {

    private File jsHint;
    private Iterable<File> source;
    private String encoding;

    public File getJsHint() {
        return jsHint;
    }

    public void setJsHint(File jsHint) {
        this.jsHint = jsHint;
    }

    public Iterable<File> getSource() {
        return source;
    }

    public void setSource(Iterable<File> source) {
        this.source = source;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

}
