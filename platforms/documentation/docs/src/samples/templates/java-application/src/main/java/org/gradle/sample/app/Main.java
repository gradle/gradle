package org.gradle.sample.app;

import org.gradle.sample.list.ArrayList;

import static org.gradle.sample.utilities.StringUtils.join;
import static org.gradle.sample.utilities.StringUtils.split;
import static org.gradle.sample.app.MessageUtils.getMessage;

public class Main {
    public static void main(String[] args) {
        ArrayList tokens;
        tokens = split(getMessage());
        System.out.println(join(tokens));
    }
}
