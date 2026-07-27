import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

abstract class ExpensiveReason : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String =
        "there is no property skipHello"
}

val hello = tasks.register("hello") {
    doLast {
        println("hello world")
    }
}

hello {
    val skipProvider = providers.gradleProperty("skipHello")
    val reason = providers.of(ExpensiveReason::class) {}
    onlyIf(reason) {
        !skipProvider.isPresent()
    }
}
