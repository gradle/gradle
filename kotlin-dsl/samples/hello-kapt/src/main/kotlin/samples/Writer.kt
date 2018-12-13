package samples

import com.google.auto.value.AutoValue

/**
 * Example of AutoValue class that implements [User] interface and provides builder
 *
 * Can be easily used from java and with AutoValue plugins
 */
@AutoValue
abstract class Writer : User {
    /**
     * Define only additional property [books]
     * other properties will be implicitly generated from properties of [User]
     */
    abstract val books: List<String>

    /**
     * Create prefilled builder from current object
     */
    abstract fun toBuilder(): Builder

    /**
     * Define builder fields.
     * We must provide all fields and build() method otherwise will get compile error
     */
    @AutoValue.Builder
    abstract class Builder {
        abstract fun name(name: String): Builder
        abstract fun age(age: Int?): Builder
        abstract fun books(books: List<String>): Builder

        fun build(): Writer {
            // Example of builder value normalization
            if (name == "") name("Anonymous")

            // Example of builder value validation
            val age = age
            require(age == null || age >= 0) { "Age can not be negative" }

            return internalBuild()
        }

        // getters to get builder value
        protected abstract val name: String
        protected abstract val age: Int?

        // Real build method hidden from user to allow us validate and normalize values
        protected abstract fun internalBuild(): Writer
    }

    companion object {
        /**
         * Creates empty [Builder]
         */
        @JvmStatic
        fun builder(): Builder = AutoValue_Writer.Builder()
                // Default value for builder
                .books(emptyList())
    }
}
