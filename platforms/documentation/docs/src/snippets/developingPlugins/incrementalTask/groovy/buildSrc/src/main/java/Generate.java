import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

// tag::snippet[]
public abstract class Generate extends DefaultTask {

    @Input
    abstract public Property<Integer> getFileCount();

    @Input
    abstract public Property<String> getContent();

    @OutputDirectory
    abstract public RegularFileProperty getGeneratedFileDir();

    @TaskAction
    public void perform() throws IOException {
        for (int i = 1; i <= getFileCount().get(); i++) {
            writeFile(new File(getGeneratedFileDir().get().getAsFile(), i + ".txt"), getContent().get());
        }
    }

    private void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
}
// end::snippet[]
