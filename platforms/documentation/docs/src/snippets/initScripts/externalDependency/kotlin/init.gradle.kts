// tag::all[]
import org.apache.commons.math.fraction.Fraction

// tag::declare-classpath[]
initscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.commons:commons-math:2.0")
    }
}
// end::declare-classpath[]

println(Fraction.ONE_FIFTH.multiply(2))
// end::all[]
