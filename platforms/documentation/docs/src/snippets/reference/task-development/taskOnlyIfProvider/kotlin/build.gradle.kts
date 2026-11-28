tasks.register("deploy") {
    onlyIf(
        providers.environmentVariable("DEPLOY_TOKEN")
            .map { OnlyIfDecision.proceed() }
            .orElse(OnlyIfDecision.skip("DEPLOY_TOKEN is not set"))
    )
    doLast {
        println("deploying")
    }
}
