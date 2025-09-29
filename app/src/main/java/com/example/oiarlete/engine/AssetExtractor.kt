package com.example.oiarlete.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class AssetExtractor(private val context: Context) {
    private val am = context.assets

    fun extractFile(assetName: String, targetSubdir: String = "engines"): String {
        val outDir = File(context.filesDir, targetSubdir)
        if (!outDir.exists()) outDir.mkdirs()
        val outFile = File(outDir, File(assetName).name)
        if (outFile.exists()) return outFile.absolutePath
        try {
            am.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                    }
                    output.flush()
                }
            }
            com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Info("Asset extraído: ${assetName} -> ${outFile.absolutePath}")
            )
        } catch (e: Exception) {
            com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Error("Falha extraindo asset ${assetName}: ${e.message}")
            )
            throw e
        }
        return outFile.absolutePath
    }

    fun extractDirectory(assetDir: String, targetSubdir: String): String {
        val baseOut = File(context.filesDir, targetSubdir)
        try {
            val children = am.list(assetDir)?.toList().orEmpty()
            com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Info("Assets listar '${assetDir}': ${children.size} itens ${if (children.isNotEmpty()) children.take(10).joinToString() else ""}")
            )
        } catch (_: Exception) { }
        if (baseOut.exists()) {
            // Verificar se parece válido (não vazio e contém alguns arquivos chave do diretório de origem)
            val contents = baseOut.listFiles()?.toList().orEmpty()
            val looksNonEmpty = contents.isNotEmpty()
            var looksValid = looksNonEmpty
            // Heurística: se nos assets existir README ou final.mdl ou conf/model.conf, exija que também exista no destino
            try {
                val hasReadme = (am.list(assetDir)?.contains("README") == true) || runCatching { am.open("$assetDir/README").close(); true }.getOrDefault(false)
                val hasFinalMdl = runCatching { am.open("$assetDir/final.mdl").close(); true }.getOrDefault(false)
                val hasConf = runCatching { am.open("$assetDir/conf/model.conf").close(); true }.getOrDefault(false)
                val hasIvector = runCatching { am.open("$assetDir/ivector/final.dubm").close(); true }.getOrDefault(false)
                if (hasReadme || hasFinalMdl || hasConf || hasIvector) {
                    val okReadme = File(baseOut, "README").exists()
                    val okFinalMdl = File(baseOut, "final.mdl").exists()
                    val okConf = File(File(baseOut, "conf"), "model.conf").exists()
                    val okIvector = File(File(baseOut, "ivector"), "final.dubm").exists()
                    looksValid = okReadme || okFinalMdl || okConf || okIvector
                }
            } catch (_: Exception) { }
            if (looksValid) return baseOut.absolutePath
            // Caso inválido: apagar e reextrair
            try { baseOut.deleteRecursively() } catch (_: Exception) {}
        }
        copyAssetDirRecursively(assetDir, baseOut)
        // Logar pós-cópia
        try {
            val outList = baseOut.listFiles()?.map { (if (it.isDirectory) "[D]" else "[F]") + it.name }?.sorted()?.take(50).orEmpty()
            com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Info("Extração dir '${assetDir}' -> '${baseOut.absolutePath}': ${outList.size} itens ${if (outList.isNotEmpty()) outList.joinToString(", ") else "(vazio)"}")
            )
        } catch (_: Exception) { }
        return baseOut.absolutePath
    }

    private fun copyAssetDirRecursively(assetDir: String, outDir: File) {
        if (!outDir.exists()) outDir.mkdirs()
        val children = am.list(assetDir) ?: return
        for (child in children) {
            val path = if (assetDir.isBlank()) child else "$assetDir/$child"
            val out = File(outDir, child)
            val subChildren = am.list(path)
            if (!subChildren.isNullOrEmpty()) {
                // Directory
                copyAssetDirRecursively(path, out)
            } else {
                // File
                try {
                    am.open(path).use { input ->
                        FileOutputStream(out).use { output ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                            }
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    com.example.oiarlete.diagnostics.DiagnosticsBus.emit(
                        com.example.oiarlete.diagnostics.DiagnosticsBus.Event.Error("Falha copiando '${path}' -> '${out.absolutePath}': ${e.message}")
                    )
                    throw e
                }
            }
        }
    }
}
