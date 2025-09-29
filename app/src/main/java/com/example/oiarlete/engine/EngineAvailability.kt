package com.example.oiarlete.engine

object EngineAvailability {
    @Volatile var porcupineAssets: Boolean = false
    @Volatile var voskAssets: Boolean = false

    // Resolved asset names/paths discovered at runtime (optional)
    @Volatile var porcupineKeywordAssetName: String? = null
    @Volatile var porcupineModelAssetName: String? = null
    @Volatile var voskModelDirName: String? = null

    // Resolved filesystem paths after extraction (if any)
    @Volatile var porcupineKeywordFilePath: String? = null
    @Volatile var porcupineModelFilePath: String? = null
    @Volatile var voskModelDirPath: String? = null
}
