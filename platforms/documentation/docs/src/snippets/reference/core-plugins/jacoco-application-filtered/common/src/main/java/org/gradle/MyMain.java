package org.gradle;

import java.lang.String;

public class MyMain{

    public static void main(String... args){
        new MyMain().someMethod();
    }

    private void someMethod(){
        System.out.println("Some output from 'MyMain'");
    }
}
