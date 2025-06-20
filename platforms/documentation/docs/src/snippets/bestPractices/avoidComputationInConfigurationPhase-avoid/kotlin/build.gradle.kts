// tag::avoid-this[]
fun heavyWork() {
    println("Start heavy work")
    Thread.sleep(50)
    println("Finish heavy work")
}

tasks.register("myTask") {
    heavyWork() // <1>
}
// end::avoid-this[]
