// Base sleep of each task in ms, picked at random once (1-5s) so it is the same on every run
val baseSleepMillis = mapOf("extraA" to 2984L, "extraB" to 4559L)

baseSleepMillis.forEach { (name, base) ->
    // Varies by 0-3s on each execution
    tasks.register(name) { doLast { Thread.sleep(base + (0L..3000L).random()) } }
}
