class StateObject {
    // ...
}

abstract class StatefulTask : DefaultTask() {
    @get:Internal
    var stateObject: StateObject? = null

    @get:Internal
    var strings: List<String>? = null
}


tasks.register<StatefulTask>("checkEquality") {
    val objectValue = StateObject()
    val stringsValue = arrayListOf("a", "b")

    stateObject = objectValue
    strings = stringsValue

    doLast { // <1>
        println("POJO reference equality: ${stateObject === objectValue}") // <2>
        println("Collection reference equality: ${strings === stringsValue}") // <3>
        println("Collection equality: ${strings == stringsValue}") // <4>
    }
}
