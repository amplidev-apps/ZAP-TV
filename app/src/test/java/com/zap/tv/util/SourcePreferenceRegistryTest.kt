package com.zap.tv.util

import com.zap.tv.data.reidoscanais.RdcEmbedDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Etapa 13: regras manuais de fontes (`docs/novas regras.md`). Estas asserções NÃO
 * validam reprodução real — apenas a decisão determinística de preferência de fonte,
 * a organização (bloco de citados) e a separação entre preferir/ignorar embeds.
 */
class SourcePreferenceRegistryTest {

    private fun embed(url: String, provider: String? = null) =
        RdcEmbedDto(provider = provider, quality = "HD", embed_url = url)

    @Test
    fun canal_premium_escolhe_provedor_premium_entre_alternativas() {
        val chosen = SourcePreferenceRegistry.preferredEmbed(
            entityChannelId = "rdc:premiereclubes",
            displayName = "Premiere",
            embeds = listOf(
                embed("https://rdcanais.org/premiereclubes", "Servidor Alternativo"),
                embed("https://rdcanais.org/premiereclubes", "Servidor Premium")
            )
        )
        assertEquals("Servidor Premium", chosen?.provider)
    }

    @Test
    fun canal_manter_fonte_existente_nao_promove_embed() {
        val chosen = SourcePreferenceRegistry.preferredEmbed(
            entityChannelId = "rdc:globonews",
            displayName = "Globo News",
            embeds = listOf(embed("https://rdcanais.org/globonews", SourcePreferenceRegistry.PREMIUM))
        )
        assertNull(chosen)
        assertEquals(
            SourcePreferenceRegistry.Preference.KEEP_EXISTING,
            SourcePreferenceRegistry.preferenceFor("rdc:globonews", "Globo News")
        )
    }

    @Test
    fun canal_pendente_nao_promove_embed() {
        val chosen = SourcePreferenceRegistry.preferredEmbed(
            entityChannelId = "rdc:jpnews",
            displayName = "JP News",
            embeds = listOf(embed("https://rdcanais.org/jpnews", SourcePreferenceRegistry.PREMIUM))
        )
        assertNull(chosen)
        assertEquals(
            SourcePreferenceRegistry.Preference.PENDING,
            SourcePreferenceRegistry.preferenceFor("rdc:jpnews", "JP News")
        )
    }

    @Test
    fun casamento_por_nome_para_canal_representado_por_id_iptv() {
        // Um canal representado reutiliza o id do iptv-org (não prefixado "rdc:").
        assertNotNull(SourcePreferenceRegistry.ruleFor("AXN.br", "AXN"))
        assertNotNull(SourcePreferenceRegistry.ruleFor("TNT.br", "TNT"))
    }

    @Test
    fun canal_fora_das_regras_nao_tem_preferencia() {
        assertNull(SourcePreferenceRegistry.preferenceFor("rdc:canalqualquer", "Canal Qualquer"))
    }

    @Test
    fun isFeatured_reconhece_canal_brasileiro_citado() {
        assertTrue(SourcePreferenceRegistry.isFeatured("rdc:tnt", "TNT", "BR"))
        assertTrue(SourcePreferenceRegistry.isFeatured("TNT.br", "TNT", "BR"))
    }

    @Test
    fun isFeatured_ignora_homonimo_estrangeiro_e_canal_fora_da_lista() {
        assertFalse(SourcePreferenceRegistry.isFeatured("TNT.us", "TNT", "US"))
        assertFalse(SourcePreferenceRegistry.isFeatured("rdc:canalqualquer", "Canal Qualquer", "BR"))
    }

    @Test
    fun observacoes_do_usuario_ficam_registradas() {
        assertNotNull(SourcePreferenceRegistry.noteFor("rdc:hbo", "HBO"))
        assertNotNull(SourcePreferenceRegistry.noteFor("rdc:discoveryworld", "Discovery World"))
    }
}
