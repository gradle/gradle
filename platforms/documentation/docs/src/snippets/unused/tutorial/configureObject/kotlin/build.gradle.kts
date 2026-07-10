class UserInfo(
    var name: String? = null,
    var email: String? = null
)

tasks.register("greet") {
    val user = UserInfo().apply {
        name = "Isaac Newton"
        email = "isaac@newton.me"
    }
    doLast {
        println(user.name)
        println(user.email)
    }
}
