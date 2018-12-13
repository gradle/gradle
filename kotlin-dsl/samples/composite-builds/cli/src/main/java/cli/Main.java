package cli;

import core.*;

public class Main {
    public static void main(String[] args) {
        int answer = DeepThought.compute();
        System.out.println(String.format("The answer to the ultimate question of Life, the Universe and Everything is %d.", answer));
    }
}
