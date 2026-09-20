package com.zap.tv.data.m3u

import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter

object M3uCatalogGenerator {

    @JvmStatic
    fun main(args: Array<String>) {
        runBlocking {
            println("Iniciando geração do catálogo M3U...")
            
            val fileNames = if (args.isNotEmpty()) args.toList() else listOf("CanaisBR01.m3u8", "CanaisBR02.m3u8", "CanaisBR03.m3u8")
            val entries = mutableListOf<M3uParser.ParsedEntry>()

            for (fileName in fileNames) {
                val inputFile = File(fileName)
                if (!inputFile.exists()) {
                    println("Arquivo não encontrado: ${inputFile.absolutePath} - ignorando...")
                    continue
                }
                
                println("Lendo: ${inputFile.absolutePath} (${inputFile.length() / 1024 / 1024} MB)")
                val inputStream = FileInputStream(inputFile)
                val parsed = M3uParser.parse(inputStream)
                entries.addAll(parsed)
            }
            
            println("Total de Entries parseados (agrupando links alternativos): ${entries.size}")
            
            val catalog = M3uChannelMapper.mapToCatalogs(entries)
            
            println("Canais BR: ${catalog.brChannels.size}")
            println("Canais PT: ${catalog.ptChannels.size}")
            println("Canais US: ${catalog.usChannels.size}")
            println("Canais Adulto: ${catalog.adultChannels.size}")
            
            val outputFile = File("app/src/main/assets/m3u_catalog.json")
            outputFile.parentFile?.mkdirs()
            
            val gson = GsonBuilder().setPrettyPrinting().create()
            val json = gson.toJson(catalog)
            
            FileWriter(outputFile).use { it.write(json) }
            
            println("Catálogo salvo em: ${outputFile.absolutePath}")
            println("Tamanho JSON: ${outputFile.length() / 1024} KB")
            
            // Estatísticas de qualidade
            val allChannels = catalog.brChannels + catalog.ptChannels + catalog.usChannels + catalog.adultChannels
            val qualityStats = mutableMapOf<String, Int>()
            for (channel in allChannels) {
                for (stream in channel.streams) {
                    qualityStats[stream.quality] = qualityStats.getOrDefault(stream.quality, 0) + 1
                }
            }
            println("Estatísticas de qualidade: $qualityStats")
        }
    }
}