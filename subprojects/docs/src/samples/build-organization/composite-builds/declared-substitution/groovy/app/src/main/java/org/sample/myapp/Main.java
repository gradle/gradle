package org.sample.myapp;

import org.sample.numberutils.Numbers;

public class Main {

    public static void main(String... args) {
        new Main().printAnswer();
    }

    public void printAnswer() {
        String output = " The answer is " + Numbers.add(19, 23);
        System.out.println(output);
    }
}
