package compile.fork;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.Arrays;

public class Person {
    String name;
    int age;

    void hello() {
        DefaultGroovyMethods.max(Arrays.asList(3, 1, 2));
    }
}