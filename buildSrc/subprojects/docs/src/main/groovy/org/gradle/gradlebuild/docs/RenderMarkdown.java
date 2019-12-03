package org.gradle.gradlebuild.docs;

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
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public abstract RegularFileProperty getMarkdownFile();

    @OutputFile
    public abstract RegularFileProperty getDestinationFile();

    @Input
    public abstract Property<String> getInputEncoding();

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
