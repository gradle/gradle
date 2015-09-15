package org.gradle;

public class Client {
    public static void main(String... args) {
	if ("1.0".equals(Utils.getCompatibility())) {
            System.out.println("Compatible!");
        }
    }
}
