import java.io.*;

// tag::broken-type[]
public class User implements Serializable {
    private transient String name;
    private transient int age;

    // ...
// end::broken-type[]
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeObject(name); // <1>
        out.writeInt(age);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        this.name = (String) in.readObject();
        // this.age = in.readInt(); // <2>
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}
