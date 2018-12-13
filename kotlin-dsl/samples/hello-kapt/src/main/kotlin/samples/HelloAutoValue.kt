package samples

fun main(args: Array<String>) {
    val user = Writer.builder()
            .name("Douglas")
            .age(41)
            .books(listOf("THGttG", "DGHDA"))
            .build()

    println("Hello $user")

    // Create builder from object and create new instance with modified value
    val userWithTheAnswer = user.toBuilder()
            .age(user.age!! + 1)
            .build()

    println("The answer is ${userWithTheAnswer.age}")
}

interface User {
    val name: String
    val age: Int?
}
