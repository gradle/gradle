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

package org.gradle.plugins.javascript.envjs.http.simple.internal;

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.IoActions;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.resource.Context;
import org.simpleframework.http.resource.Index;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;

public class SimpleFileServerContainer implements Container {

    private final Context context;

    public SimpleFileServerContainer(Context context) {
        this.context = context;
    }

    @Override
    public void handle(Request req, Response resp) {
        Index requestIndex = context.getIndex(req.getTarget());
        File targetFile = requestIndex.getFile();

        if (!targetFile.exists()) {
            resp.setCode(404);
            resp.setText("Not Found");
            try {
                resp.getPrintStream().println(String.format("File '%s' does not exist", targetFile.getAbsolutePath()));
                resp.commit();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String contentType = requestIndex.getContentType();
        resp.set("Content-Type", contentType);

        OutputStream output = null;
        try {
            output = resp.getOutputStream();

            if (contentType.startsWith("text/")) {
                resp.set("Content-Encoding", Charset.defaultCharset().name());
                Reader input = new FileReader(requestIndex.getFile());
                IOUtils.copy(input, output, Charset.defaultCharset());
                IoActions.closeQuietly(input);
            } else {
                InputStream input = new FileInputStream(requestIndex.getFile());
                IOUtils.copy(input, output);
                IoActions.closeQuietly(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IoActions.closeQuietly(output);
        }
    }
}
