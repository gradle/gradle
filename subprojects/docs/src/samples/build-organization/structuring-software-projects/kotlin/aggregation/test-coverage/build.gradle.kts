plugins {
    id("com.example.report-aggregation")
}

dependencies {
    // Trasitively collect coverage data from all features and their dependencies
    jacocoAggregation("com.example.myproduct.user-feature:table")
    jacocoAggregation("com.example.myproduct.admin-feature:config")
}
