package earth.terrarium.cloche.target

interface ClientTarget {
    val client: RunnableCompilation

    fun noClient()

    fun includeClient()
}
