package org.example;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

import java.util.List;

// tag::provider[]
public abstract class ConfigFileArgumentProvider implements CommandLineArgumentProvider {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getConfigFile();

    @Override
    public Iterable<String> asArguments() {
        return List.of("-Dapp.config=" + getConfigFile().get().getAsFile().getAbsolutePath());
    }
}
// end::provider[]
