package org.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.io.IOException;

@CacheableTask
public abstract class MyTask extends DefaultTask {
    @Input
    public abstract Property<String> getFirstName();
    @Input
    public abstract Property<String> getLastName();
    @Input
    public abstract Property<String> getGreeting();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input // <1>
    public abstract Property<Instant> getToday();

    @TaskAction
    public void run() throws IOException {
        File output = getOutputFile().getAsFile().get();
        String result = String.format("%s, %s %s, it's currently\n%s", getGreeting().get(), getFirstName().get(), getLastName().get(), getToday().get());
        System.out.println(result);
        Files.writeString(output.toPath(), result);
    }
}
