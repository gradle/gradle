import org.gradle.api.provider.Property

interface ProjectProperties { // <1>
    Property<String> getPropertyA()
    Property<String> getPropertyB()
}
