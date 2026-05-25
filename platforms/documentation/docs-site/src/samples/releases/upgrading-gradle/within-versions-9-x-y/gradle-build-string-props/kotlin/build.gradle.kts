val myIntProp = 42

tasks.register<GradleBuild>("nestedBuild") {
    startParameter.projectProperties.put("myIntProp", "$myIntProp") // Convert int to String
}
