package com.example.oiarlete.engine

import android.content.Context

class EngineAssetsProvider(private val context: Context) {
    /**
     * Tenta detectar um arquivo de keyword do Porcupine nos assets.
     * Regras:
     * - Procura por qualquer arquivo com extensão .ppn na raiz de assets/ ou em assets/porcupine/
     * - Se keywordAssetName for fornecido, valida especificamente esse arquivo.
     * Retorna o nome do asset se encontrado, ou null.
     */
    fun findPorcupineKeywordAsset(keywordAssetName: String? = null): String? {
        try {
            val am = context.assets
            // Se explicitamente informado, valida
            if (!keywordAssetName.isNullOrBlank()) {
                am.open(keywordAssetName).close()
                return keywordAssetName
            }
            // Procurar na raiz e validar abrindo
            am.list("")?.firstOrNull { it.endsWith(".ppn", ignoreCase = true) }?.let {
                try { am.open(it).close(); return it } catch (_: Exception) {}
            }
            // Procurar em subpasta comum e validar
            am.list("porcupine")?.firstOrNull { it.endsWith(".ppn", ignoreCase = true) }?.let {
                val path = "porcupine/$it"
                try { am.open(path).close(); return path } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // ignore
        }
        return null
    }

    /**
     * Detecta um arquivo de modelo do Porcupine (.pv) compatível com o idioma da keyword (.ppn).
     * Heurística:
     * - Se o nome do .ppn contém "_pt" ou termina com "pt", preferir modelos pt/pt-br; senão usar en por padrão.
     * - Procura na raiz ou em assets/porcupine/ por arquivos .pv com nomes típicos (porcupine_params_pt.pv, porcupine_params_pt-br.pv, porcupine_params_en.pv).
     */
    fun findPorcupineModelAsset(keywordAssetName: String?, preferredLang: String? = null): String? {
        val am = context.assets
        fun exists(path: String): Boolean = try { am.open(path).close(); true } catch (_: Exception) { false }
        fun havePt(): Boolean =
            exists("porcupine_params_pt-br.pv") ||
            exists("porcupine_params_pt.pv") ||
            exists("porcupine/porcupine_params_pt-br.pv") ||
            exists("porcupine/porcupine_params_pt.pv")
        fun haveEn(): Boolean =
            exists("porcupine_params_en.pv") ||
            exists("porcupine/porcupine_params_en.pv")

        val pref = preferredLang?.lowercase()
        val languages = when (pref) {
            "pt-br", "ptbr", "pt_br" -> listOf("pt-br", "pt")
            "pt" -> listOf("pt", "pt-br")
            "en", "en-us", "en_gb" -> listOf("en")
            else -> {
                val isPtFromKeyword = keywordAssetName?.lowercase()?.contains("pt") == true
                if (isPtFromKeyword) {
                    listOf("pt-br", "pt")
                } else {
                    // AUTO: se só houver pt disponível, preferir pt
                    val pt = havePt()
                    val en = haveEn()
                    when {
                        pt && !en -> listOf("pt-br", "pt")
                        else -> listOf("en")
                    }
                }
            }
        }

        val candidates = languages.flatMap { lang ->
            when (lang) {
                "pt-br" -> listOf(
                    "porcupine_params_pt-br.pv",
                    "porcupine/porcupine_params_pt-br.pv"
                )
                "pt" -> listOf(
                    "porcupine_params_pt.pv",
                    "porcupine/porcupine_params_pt.pv"
                )
                else -> listOf(
                    "porcupine_params_en.pv",
                    "porcupine/porcupine_params_en.pv"
                )
            }
        }.toMutableList()
        return candidates.firstOrNull { exists(it) }
    }

    /**
     * Tenta detectar uma pasta de modelo do Vosk nos assets.
     * Regras:
     * - Procura por diretórios na raiz que contenham arquivos típicos do Vosk (e.g., conf/model.conf ou README)
     * - Verifica também assets/vosk-model como default
     * Retorna o nome da pasta (relativo à raiz dos assets) se encontrada, ou null.
     */
    fun findVoskModelDir(modelDirName: String? = null): String? {
        val am = context.assets
        fun looksLikeVoskDir(dir: String): Boolean {
            return try {
                // Alguns modelos têm um README ou arquivo conf
                am.open("$dir/README").close(); true
            } catch (_: Exception) {
                try { am.open("$dir/conf/model.conf").close(); true } catch (_: Exception) {
                    try { am.open("$dir/ivector/final.dubm").close(); true } catch (_: Exception) { false }
                }
            }
        }

        // Se informado, apenas valida
        if (!modelDirName.isNullOrBlank()) {
            if (looksLikeVoskDir(modelDirName)) return modelDirName
        }

        // Candidatos na raiz
        val roots = try { am.list("")?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        for (entry in roots) {
            // pular arquivos simples
            // Não há API direta para checar se é diretório, tentamos abrir um arquivo típico
            if (looksLikeVoskDir(entry)) return entry
        }
        // Candidatos padrão
        if (looksLikeVoskDir("vosk-model")) return "vosk-model"
        if (looksLikeVoskDir("bosk-model")) return "bosk-model" // tolerância a digitação
        return null
    }
}
