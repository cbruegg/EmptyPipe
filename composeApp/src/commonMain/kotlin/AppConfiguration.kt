import platform.Foundation.NSUserDefaults

data class AppConfiguration(
    val pipedApiInstanceUrl: String?
) {
    companion object {
        fun loadFrom(userDefaults: NSUserDefaults): AppConfiguration {
            return AppConfiguration(
                userDefaults.stringForKey("pipedApiInstanceUrl")
            )
        }
    }
}

fun AppConfiguration.saveTo(userDefaults: NSUserDefaults) {
    userDefaults.setObject(pipedApiInstanceUrl, "pipedApiInstanceUrl")
}