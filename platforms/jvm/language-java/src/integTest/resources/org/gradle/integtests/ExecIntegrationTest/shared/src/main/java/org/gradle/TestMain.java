package org.gradle;

import java.io.File;

public class TestMain {
    public static void main(String[] args) throws Exception {
        File expectedWorkingDir = new File(args[0]).getCanonicalFile();
        File actualWorkingDir = new File(System.getProperty("user.dir")).getCanonicalFile();
        if (!expectedWorkingDir.getCanonicalFile().equals(actualWorkingDir)) {
            throw new RuntimeException(String.format("Unexpected working directory '%s', expected '%s'.", actualWorkingDir, expectedWorkingDir));
        }
        File file = new File(args[1]);
        file.getParentFile().mkdirs();
        file.createNewFile();
        System.out.println("Created file " + file.getCanonicalPath());
    }
}
