import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

abstract public class CreateMD5 extends SourceTask { // <1>

    @OutputDirectory
    abstract public DirectoryProperty getDestinationDirectory(); // <2>

    @TaskAction
    public void createHashes() {
        for (File sourceFile : getSource().getFiles()) { // <3>
            try {
                InputStream stream = new FileInputStream(sourceFile);
                System.out.println("Generating MD5 for " + sourceFile.getName() + "...");
                // Artificially make this task slower.
                Thread.sleep(3000); // <4>
                Provider<RegularFile> md5File = getDestinationDirectory().file(sourceFile.getName() + ".md5");  // <5>
                FileUtils.writeStringToFile(md5File.get().getAsFile(), DigestUtils.md5Hex(stream), (String) null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
