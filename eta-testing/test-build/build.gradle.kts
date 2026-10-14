val intro = tasks.register("intro") {
    doLast {
        println("36 normal tasks (1-5s each, varying by up to 3s per run) run in parallel, then a slow task (12-15s) runs after all of them.")
        println("Shows the ETA accounting for a long task at the end of the build.")
    }
}

// Base sleep of each task in ms, picked at random once (1-5s) so it is the same on every run
val baseSleepMillis = listOf(
    2612, 4771, 3240, 1038, 2407, 4291, 1502, 3717, 1099, 4099,
    2734, 4124, 2571, 4053, 2602, 1753, 4859, 2493, 1921, 1526,
    4810, 1075, 3125, 3158, 1448, 1756, 4454, 3009, 4423, 1678,
    4070, 3874, 3487, 1193, 4332, 2135, 1679, 2957, 4155, 3459,
    3221, 3904, 1833, 3279, 2418, 4028, 4924, 3937, 3350, 4739,
    4137, 1663, 2622, 2628, 1851, 4997, 4129, 2723, 2679, 4867,
    3884, 4223, 1884, 3986, 3922, 3411, 3186, 3625, 2910, 3908,
    2484, 2283, 3664, 2998, 2404, 4363, 4559, 2363, 2197, 1216,
    4449, 4127, 4932, 4935, 4479, 2867, 4532, 2386, 2865, 3113,
    2194, 3196, 2486, 3112, 3957, 3512, 4457, 1435, 4370, 1572,
    1777, 3546, 1701, 1713, 1421, 3125, 2360, 1580, 3736, 1146,
    3023, 1357, 3504, 3190, 4939, 2157, 2748, 1704, 4407, 3004
)

(1..120).forEach { i ->
    val base = baseSleepMillis[i - 1].toLong()
    // Varies by 0-3s on each execution
    tasks.register("normal$i") {
        dependsOn(intro)
        doLast { Thread.sleep(base + (0L..3000L).random()) }
    }
}
// Runs after all the normal tasks, taking 12-15s, picked at random on each execution
tasks.register("slow") {
    dependsOn(intro)
    dependsOn((1..36).map { "normal$it" })
    doLast { Thread.sleep((12000L..15000L).random()) }
}
tasks.register("all") { dependsOn(intro, "slow") }
