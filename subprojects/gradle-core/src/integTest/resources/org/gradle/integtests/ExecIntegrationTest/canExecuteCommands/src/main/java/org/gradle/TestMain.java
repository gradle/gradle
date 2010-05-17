package org.gradle;

import java.io.File;

public class TestMain {
    public static void main(String[] args) throws Exception {
        new File(args[0]).createNewFile();
    }
}