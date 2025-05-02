public class Main {
    public static void main(String... args) {
        new ServiceRegistry().createService1();
        new Entity1Repository().save(new Entity1());
    }
}
