package org.gradle.sample.utilities;

import org.gradle.sample.list.ArrayList;

public class StringUtils {
    public static String join(ArrayList source) {
        return JoinUtils.join(source);
    }

    public static ArrayList split(String source) {
        return SplitUtils.split(source);
    }
}
