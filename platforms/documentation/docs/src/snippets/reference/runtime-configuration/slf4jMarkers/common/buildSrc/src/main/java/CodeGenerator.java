import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

// Depends only on slf4j-api
public class CodeGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodeGenerator.class);

    private final Marker statusMarker;

    // Used when the code generator runs on its own
    public CodeGenerator() {
        this(null);
    }

    public CodeGenerator(Marker statusMarker) {
        this.statusMarker = statusMarker;
    }

    public void run() {
        LOGGER.info(statusMarker, "Processing 42 files");
    }
}
