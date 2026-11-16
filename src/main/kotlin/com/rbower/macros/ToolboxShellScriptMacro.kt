package com.rbower.macros

import com.intellij.ide.macro.Macro
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import java.io.File

class ToolboxShellScriptMacro : Macro() {

    override fun getName(): String = "ToolboxShellScript"

    override fun getDescription(): String = "Path to the Toolbox shell script for the current IDE"

    override fun expand(dataContext: DataContext): String? {
        val toolboxDir = resolveToolboxDir() ?: return null
        val scriptsLocation = readShellScriptsLocation(toolboxDir) ?: return null
        val productCode = ApplicationInfo.getInstance().build.productCode
        val scriptName = findShellScriptName(toolboxDir, productCode) ?: return null
        return File(scriptsLocation, scriptName).absolutePath
    }

    private fun resolveToolboxDir(): File? {
        val dir = when {
            SystemInfo.isMac -> File(System.getProperty("user.home"), "Library/Application Support/JetBrains/Toolbox")
            SystemInfo.isLinux -> File(System.getProperty("user.home"), ".local/share/JetBrains/Toolbox")
            SystemInfo.isWindows -> System.getenv("LOCALAPPDATA")?.let { File(it, "JetBrains/Toolbox") }
            else -> null
        }
        return dir?.takeIf { it.isDirectory }
    }

    private fun readShellScriptsLocation(toolboxDir: File): String? {
        val settingsFile = File(toolboxDir, ".settings.json")
        if (!settingsFile.isFile) return null
        val text = settingsFile.readText()
        val match = Regex(""""location"\s*:\s*"([^"]+)"""").find(
            extractJsonBlock(text, "shell_scripts") ?: return null
        )
        return match?.groupValues?.get(1)
    }

    private fun findShellScriptName(toolboxDir: File, productCode: String): String? {
        val channelsDir = File(toolboxDir, "channels")
        if (!channelsDir.isDirectory) return null
        val productCodePattern = Regex(""""productCode"\s*:\s*"([^"]+)"""")
        val scriptNamePattern = Regex(""""shellScriptName"\s*:\s*"([^"]+)"""")
        for (file in channelsDir.listFiles { _, name -> name.endsWith(".json") } ?: return null) {
            val text = file.readText()
            val code = productCodePattern.find(text)?.groupValues?.get(1) ?: continue
            if (code == productCode) {
                return scriptNamePattern.find(text)?.groupValues?.get(1)
            }
        }
        return null
    }

    private fun extractJsonBlock(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex == -1) return null
        val braceStart = json.indexOf('{', keyIndex)
        if (braceStart == -1) return null
        var depth = 0
        for (i in braceStart until json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return json.substring(braceStart, i + 1)
                }
            }
        }
        return null
    }
}
