import com.google.common.base.Optional;

public class MyClass {
    public <T> Optional<T> createOptional() {
        return Optional.absent();
    }
}
