/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;

public class LineFeedPrintWriter extends PrintWriter {
    public LineFeedPrintWriter(Writer out) {
        super(out);
    }

    public LineFeedPrintWriter(Writer out, boolean autoFlush) {
        super(out, autoFlush);
    }

    public LineFeedPrintWriter(OutputStream out) {
        super(out);
    }

    public LineFeedPrintWriter(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
    }

    public LineFeedPrintWriter(OutputStream out, boolean autoFlush, Charset charset) {
        super(out, autoFlush, charset);
    }

    public LineFeedPrintWriter(String fileName) throws FileNotFoundException {
        super(fileName);
    }

    public LineFeedPrintWriter(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(fileName, csn);
    }

    public LineFeedPrintWriter(String fileName, Charset charset) throws IOException {
        super(fileName, charset);
    }

    public LineFeedPrintWriter(File file) throws FileNotFoundException {
        super(file);
    }

    public LineFeedPrintWriter(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, csn);
    }

    public LineFeedPrintWriter(File file, Charset charset) throws IOException {
        super(file, charset);
    }

    @Override
    public void println() {
        write('\n');
    }
}
