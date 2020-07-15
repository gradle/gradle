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

package gradlebuild.docs;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Collections;

/**
 * Generates release notes file from markdown to HTML
 */
@CacheableTask
public abstract class RenderMarkdown extends DefaultTask {
    /**
     * The source markdown file.
     */
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public abstract RegularFileProperty getMarkdownFile();

    /**
     * The rendered HTML file
     */
    @OutputFile
    public abstract RegularFileProperty getDestinationFile();

    /**
     * Encoding of input file
     */
    @Input
    public abstract Property<String> getInputEncoding();

    /**
     * Encoding of output file
     */
    @Input
    public abstract Property<String> getOutputEncoding();

    @TaskAction
    public void process() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Collections.singletonList(TablesExtension.create()));
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        File markdownFile = getMarkdownFile().get().getAsFile();
        Charset inputEncoding = Charset.forName(getInputEncoding().get());

        File destination = getDestinationFile().get().getAsFile();
        Charset outputEncoding = Charset.forName(getOutputEncoding().get());
        try (InputStreamReader inputStream = new InputStreamReader(new FileInputStream(markdownFile), inputEncoding);
             OutputStreamWriter outputStream = new OutputStreamWriter(new FileOutputStream(destination), outputEncoding)) {
            String html = renderer.render(parser.parseReader(inputStream));
            outputStream.write(html);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
