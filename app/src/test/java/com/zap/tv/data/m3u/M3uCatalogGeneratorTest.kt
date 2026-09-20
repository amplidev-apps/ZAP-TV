package com.zap.tv.data.m3u

import org.junit.Test
import java.io.File

class M3uCatalogGeneratorTest {
    @Test
    fun runGenerator() {
        val userDir = System.getProperty("user.dir")
        println("Working directory is: $userDir")
        
        val baseDir = if (userDir.endsWith("app")) "../" else "./"
        
        M3uCatalogGenerator.main(arrayOf(
            "${baseDir}CanaisBR01.m3u8",
            "${baseDir}CanaisBR02.m3u8",
            "${baseDir}CanaisBR03.m3u8"
        ))
    }
}
