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

package org.gradle.nativebinaries.toolchain.internal;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.compile.ArgWriter;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class OptionsFileArgsTransformer<T extends BinaryToolSpec> implements ArgsTransformer<T> {
    private final Transformer<ArgWriter, PrintWriter> argWriterFactory;
    private final ArgsTransformer<T> delegate;

    public OptionsFileArgsTransformer(Transformer<ArgWriter, PrintWriter> argWriterFactory, ArgsTransformer<T> delegate) {
        this.argWriterFactory = argWriterFactory;
        this.delegate = delegate;
    }

    public List<String> transform(T spec) {
        List<String> output = new ArrayList<String>();
        transformArgs(delegate.transform(spec), output, spec.getTempDir());
        return output;
    }

    protected void transformArgs(List<String> input, List<String> output, File tempDir) {
        GFileUtils.mkdirs(tempDir);
        File optionsFile = new File(tempDir, "options.txt");
        try {
            PrintWriter writer = new PrintWriter(optionsFile);
            try {
                ArgWriter argWriter = argWriterFactory.transform(writer);
                argWriter.args(input);
            } finally {
                IOUtils.closeQuietly(writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write compiler options file '%s'.", optionsFile.getAbsolutePath()), e);
        }

        output.add(String.format("@%s", optionsFile.getAbsolutePath()));
    }
}
