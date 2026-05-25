    // setting convention from constructor
    @get:Input
    abstract val guest: Property<String>

    init {
        guest.convention("person2")
    }
