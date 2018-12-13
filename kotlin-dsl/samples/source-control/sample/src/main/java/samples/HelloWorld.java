package samples;

import compute.*;

public class HelloWorld {

    public static void main(String[] args) {
        System.out.println(greeting());
    }

    static String greeting() {
        int answer = DeepThought.compute();
        return String.format("The answer to the ultimate question of Life, the Universe and Everything is %d.", answer);
    }
}
