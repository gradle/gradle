task taskX {
    doLast {
        println 'taskX'
        throw new RuntimeException()
    }
}
task taskY {
    doLast {
        println 'taskY'
    }
}

taskX.finalizedBy taskY