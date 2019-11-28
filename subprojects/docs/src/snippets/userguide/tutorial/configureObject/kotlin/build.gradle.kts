import java.text.FieldPosition

tasks.register("configure") {
    doLast {
        val pos = FieldPosition(10).apply {
            beginIndex = 1
            endIndex = 5
        }
        println(pos.beginIndex)
        println(pos.endIndex)
    }
}
