import com.test.SimpleAnnotation

@SimpleAnnotation
class Person {
    String name
    int age
    Address address

    void sing() {
        println "tra-la-la"
    }
}
