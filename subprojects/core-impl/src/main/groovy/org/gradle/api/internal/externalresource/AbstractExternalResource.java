/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.externalresource;

import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.util.CopyProgressListener;
import org.apache.ivy.util.FileUtil;

import java.io.*;

public abstract class AbstractExternalResource implements ExternalResource {
    public void writeTo(File destination, CopyProgressListener progress) throws IOException {
        FileOutputStream output = new FileOutputStream(destination);
        writeTo(output, progress);
        output.close();
    }

    public void writeTo(OutputStream output, CopyProgressListener progress) throws IOException {
        InputStream input = openStream();
        try {
            FileUtil.copy(input, output, progress);
        } finally {
            input.close();
        }
    }


    public Resource clone(String cloneName) {
        throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
    }

}
