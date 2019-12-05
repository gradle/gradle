package org.gradle.sample;

import org.gradle.sample.LinkedList;

import static org.gradle.sample.StringUtils.join;
import static org.gradle.sample.StringUtils.split;
import static org.gradle.sample.MessageUtils.getMessage;

public class Main {
    public static void main(String[] args) {
        LinkedList tokens;
        tokens = split(getMessage());
        System.out.println(join(tokens));
    }
}
