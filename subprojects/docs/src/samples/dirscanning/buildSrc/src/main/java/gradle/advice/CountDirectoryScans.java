
package gradle.advice;

import net.bytebuddy.asm.Advice;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CountDirectoryScans {
    public static boolean TRACK_LOCATIONS = false;
    public static Map<File, Integer> COUNTS = new HashMap<File, Integer>();
    public static Map<File, List<Exception>> LOCATIONS = new HashMap<File, List<Exception>>();

    @Advice.OnMethodEnter
    public synchronized static void interceptVisitFrom(@Advice.Argument(1) File fileOrDirectory) {
        File key = fileOrDirectory.getAbsoluteFile();
        Integer count = COUNTS.get(key);
        COUNTS.put(key, count != null ? count+1 : 1);

        if(TRACK_LOCATIONS) {
            List<Exception> locations = LOCATIONS.get(key);
            if(locations == null) {
               locations = new ArrayList<Exception>();
               LOCATIONS.put(key, locations);
            }
            locations.add(new Exception());
        }
    }

    public synchronized static void reset() {
        COUNTS.clear();
        LOCATIONS.clear();
    }
}
