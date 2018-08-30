package org.gradle.sample;

import org.apache.commons.collections.list.GrowthList;
public class Main {
    public static void main(String[] args) {
        GrowthList l = new GrowthList();
        if(System.getProperty("greeting.language").equals("en")){
            System.out.println("Greetings from the sample application.");
        }else{
            System.out.println("Bonjour, monde!");
        }
    }
}