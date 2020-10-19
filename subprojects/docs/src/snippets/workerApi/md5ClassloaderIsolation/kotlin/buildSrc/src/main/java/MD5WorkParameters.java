import org.gradle.api.file.RegularFileProperty;
import org.gradle.workers.WorkParameters;

public interface MD5WorkParameters extends WorkParameters {
    RegularFileProperty getSourceFile();
    RegularFileProperty getMD5File();
}