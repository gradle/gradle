import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.gradle.workers.WorkAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public abstract class GenerateMD5 implements WorkAction<MD5WorkParameters> {
    @Override
    public void execute() {
        try {
            File sourceFile = getParameters().getSourceFile().getAsFile().get();
            File md5File = getParameters().getMD5File().getAsFile().get();
            InputStream stream = new FileInputStream(sourceFile);
            System.out.println("Generating MD5 for " + sourceFile.getName() + "...");
            // Artificially make this task slower.
            Thread.sleep(3000);
            FileUtils.writeStringToFile(md5File, DigestUtils.md5Hex(stream), (String) null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
