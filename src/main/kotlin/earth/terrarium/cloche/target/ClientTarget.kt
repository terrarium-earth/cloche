package earth.terrarium.cloche.target

interface ClientTarget : ClocheTarget {
    val client: RunnableCompilation?

    fun noClient()

    fun includeClient()
}
