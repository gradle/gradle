package org.example;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List.of(new GuavaUtil(), new CommonsUtil()).forEach(util -> {
            System.out.println(util.greet());
        });
    }
}
