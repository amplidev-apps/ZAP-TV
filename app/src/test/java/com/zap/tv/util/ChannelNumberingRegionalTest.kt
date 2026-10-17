package com.zap.tv.util

import com.zap.tv.data.local.entity.ChannelNumberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da política única de regionalização (Etapa 05).
 * Cobre: afiliada da UF, fallback nacional, ausência de afiliada, proibição de
 * afiliada de outro estado, identificação de UF, IDs reais, estabilidade de
 * numeração, ausência de duplicação e preservação dos canais 8 e 9.
 *
 * NÃO executados nesta etapa (compilação/testes adiados).
 */
class ChannelNumberingRegionalTest {

    private fun ent(
        id: String,
        name: String,
        streams: Int = 3,
        country: String = "BR",
        categories: String = "general"
    ) = ChannelNumberEntity(
        number = 0,
        channelId = id,
        channelName = name,
        country = country,
        categories = categories,
        logoUrl = null,
        isNsfw = false,
        streamCount = streams,
        affiliateState = null
    )

    /** Catálogo sintético com todas as fontes relevantes para os cenários. */
    private fun catalogo() = listOf(
        // Globo: afiliadas de CE e SP + cabeça nacional
        ent("globo_tv_verdes_mares.br", "Globo TV Verdes Mares"),
        ent("globo_sp.br", "Globo SP"),
        ent("globo_rj.br", "Globo RJ"),
        ent("RedeGlobo.br", "Globo"),
        // SBT: afiliadas SP/RJ + cabeça nacional
        ent("sbt_sp.br", "SBT SP"),
        ent("sbt_rj.br", "SBT RJ"),
        ent("SBTNacional.br", "SBT Nacional"),
        // Record: cabeça nacional (sem afiliada em CE)
        ent("Record.br", "Record"),
        // Band
        ent("band.br", "Band"),
        ent("band_sp.br", "Band SP"),
        // RedeTV / Cultura / Gazeta (cabeças)
        ent("RedeTV.br", "RedeTV!"),
        ent("TVCultura.br", "TV Cultura"),
        ent("TVGazeta.br", "TV Gazeta"),
        // Canais preservados
        ent("tv_centro_sul_br", "TV Centro Sul", streams = 1),
        ent("TVLitoralRN.br", "TV Litoral RN", streams = 1)
    )

    private fun fixedByNumber(uf: String): Map<Int, ChannelNumberEntity> =
        ChannelNumbering.resolveFixedEntities(catalogo(), uf).first.associateBy { it.number }

    @Test
    fun seleciona_afiliada_da_uf_escolhida() {
        val byNum = fixedByNumber("CE")
        assertEquals("globo_tv_verdes_mares.br", byNum[1]?.channelId)
        assertEquals("Globo", byNum[1]?.channelName)
    }

    @Test
    fun usa_cabeca_nacional_quando_estado_nao_tem_afiliada() {
        val byNum = fixedByNumber("CE")
        // CE não possui SBT nem Record reproduzíveis -> cabeça de rede nacional.
        assertEquals("SBTNacional.br", byNum[2]?.channelId)
        assertEquals("Record.br", byNum[3]?.channelId)
    }

    @Test
    fun nunca_usa_afiliada_de_outro_estado_como_fallback() {
        val byNum = fixedByNumber("CE")
        // sbt_sp.br existe no catálogo, mas NÃO pode ocupar a posição 2 de CE.
        assertNotEquals("sbt_sp.br", byNum[2]?.channelId)
        assertEquals("SBTNacional.br", byNum[2]?.channelId)

        val byNumBa = fixedByNumber("BA")
        assertNotEquals("globo_sp.br", byNumBa[1]?.channelId)
        assertEquals("RedeGlobo.br", byNumBa[1]?.channelId) // BA sem afiliada no catálogo sintético
    }

    @Test
    fun preserva_tv_centro_sul_no_canal_8() {
        val byNum = fixedByNumber("CE")
        assertEquals(8, byNum[8]?.number)
        assertEquals("tv_centro_sul_br", byNum[8]?.channelId)
        assertEquals("TV Centro Sul", byNum[8]?.channelName)
    }

    @Test
    fun preserva_tv_litoral_rn_no_canal_9() {
        val byNum = fixedByNumber("CE")
        assertEquals(9, byNum[9]?.number)
        assertEquals("TVLitoralRN.br", byNum[9]?.channelId)
    }

    @Test
    fun numeracao_fixa_e_estavel_entre_estados() {
        val ce = fixedByNumber("CE")
        val sp = fixedByNumber("SP")
        assertEquals((1..9).toList(), ce.keys.sorted())
        assertEquals((1..9).toList(), sp.keys.sorted())
        // Números fixos independentes da UF; só o canal associado muda.
        for (n in 1..9) {
            assertEquals("posição $n divergente entre UFs", true, ce.containsKey(n) && sp.containsKey(n))
        }
        assertEquals("globo_tv_verdes_mares.br", ce[1]?.channelId)
        assertEquals("globo_sp.br", sp[1]?.channelId)
    }

    @Test
    fun sem_canais_duplicados_entre_posicoes_fixas() {
        val (fixed, assigned) = ChannelNumbering.resolveFixedEntities(catalogo(), "SP")
        val ids = fixed.map { it.channelId }
        assertEquals("IDs fixos duplicados", ids.size, ids.toSet().size)
        assertEquals("assigned deve casar com os fixos", ids.toSet(), assigned)
    }

    @Test
    fun resolucao_e_deterministica() {
        val a = ChannelNumbering.resolveFixedEntities(catalogo(), "CE")
        val b = ChannelNumbering.resolveFixedEntities(catalogo(), "CE")
        assertEquals(
            a.first.map { it.number to it.channelId },
            b.first.map { it.number to it.channelId }
        )
    }

    @Test
    fun troca_de_estado_altera_apenas_o_canal_da_rede() {
        val ce = fixedByNumber("CE")
        val rj = fixedByNumber("RJ")
        assertNotEquals(ce[1]?.channelId, rj[1]?.channelId) // Verdes Mares vs Globo RJ
        assertEquals("globo_rj.br", rj[1]?.channelId)
        assertEquals("globo_tv_verdes_mares.br", ce[1]?.channelId)
        // Preservados em ambos
        assertEquals("tv_centro_sul_br", ce[8]?.channelId)
        assertEquals("tv_centro_sul_br", rj[8]?.channelId)
    }

    @Test
    fun identificacao_de_uf_prioriza_metadado_verificado() {
        // Associações verificadas (AffiliateRegistry).
        assertEquals("CE", ChannelNumbering.getAffiliateState("globo_tv_verdes_mares.br", "Globo TV Verdes Mares"))
        assertEquals("AM", ChannelNumbering.getAffiliateState("recordtv_manaus.br", "RecordTV Manaus"))
        assertEquals("SP", ChannelNumbering.getAffiliateState("globo_sp.br", "Globo SP"))
    }

    @Test
    fun identificacao_de_uf_nao_infere_por_palavras_no_nome() {
        // Nome contém "SP", mas o identificador é desconhecido -> região desconhecida.
        assertNull(ChannelNumbering.getAffiliateState("canal_desconhecido.br", "Canal SP Qualquer"))
        // Cabeça nacional não pertence a UF.
        assertNull(ChannelNumbering.getAffiliateState("RedeGlobo.br", "Globo"))
    }

    @Test
    fun posicao_fica_ausente_quando_nem_afiliada_nem_cabeca_existem() {
        // Catálogo sem nenhuma fonte de SBT: posição 2 não é criada (numeração preservada).
        val semSbt = catalogo().filter { !it.channelId.contains("sbt", ignoreCase = true) }
        val byNum = ChannelNumbering.resolveFixedEntities(semSbt, "CE").first.associateBy { it.number }
        assertNull(byNum[2])
        // Demais posições continuam existindo.
        assertEquals("globo_tv_verdes_mares.br", byNum[1]?.channelId)
        assertEquals("tv_centro_sul_br", byNum[8]?.channelId)
    }

    @Test
    fun nao_muta_as_entidades_de_entrada() {
        val original = catalogo()
        val snapshot = original.map { it.number to it.channelId }
        ChannelNumbering.resolveFixedEntities(original, "CE")
        assertEquals(snapshot, original.map { it.number to it.channelId })
        assertTrue(original.all { it.number == 0 })
    }

    // ------------------------------------------------------------------
    // Risco A: seleção por nome/palavra-chave não pode escolher afiliada
    // de outro estado quando falta a cabeça nacional.
    // ------------------------------------------------------------------
    @Test
    fun risco_a_keyword_nao_seleciona_afiliada_de_outro_estado() {
        // Sem cabeça nacional de Record; presentes apenas afiliadas de SP e RJ.
        val ents = listOf(
            ent("globo_tv_verdes_mares.br", "Globo TV Verdes Mares"), // posição 1 (CE) válida
            ent("recordtv_sp.br", "RecordTV SP"),
            ent("recordtv_rj.br", "RecordTV RJ"),
            ent("tv_centro_sul_br", "TV Centro Sul", streams = 1)
        )
        val byNum = ChannelNumbering.resolveFixedEntities(ents, "CE").first.associateBy { it.number }
        // Palavra-chave "recordtv" casaria com recordtv_sp.br/rj.br; a rede de
        // segurança impede o uso e a posição fica indisponível (não pega outro estado).
        assertNull(byNum[3])
        assertEquals("globo_tv_verdes_mares.br", byNum[1]?.channelId)
    }

    @Test
    fun risco_a_sem_afiliada_e_sem_cabeca_nao_usa_outro_estado_para_sbt() {
        val ents = listOf(
            ent("sbt_sp.br", "SBT Sao Paulo"),
            ent("globo_tv_verdes_mares.br", "Globo TV Verdes Mares"),
            ent("tv_centro_sul_br", "TV Centro Sul", streams = 1)
        )
        val byNum = ChannelNumbering.resolveFixedEntities(ents, "CE").first.associateBy { it.number }
        assertNull(byNum[2])
    }

    // ------------------------------------------------------------------
    // Risco B: o fallback por nome da TV Centro-Sul não pode reconhecer
    // um canal diferente cujo nome apenas contenha "Centro Sul".
    // ------------------------------------------------------------------
    @Test
    fun risco_b_nome_parecido_nao_e_confundido_com_tv_centro_sul() {
        val ents = listOf(
            ent("globo_tv_verdes_mares.br", "Globo TV Verdes Mares"),
            ent("outro_canal.br", "Programa Centro Sul Especial") // contém "Centro Sul"
        )
        val byNum = ChannelNumbering.resolveFixedEntities(ents, "CE").first.associateBy { it.number }
        // Nenhum canal com ID/​nome exato da TV Centro-Sul -> posição 8 indisponível.
        assertNull(byNum[8])
    }

    @Test
    fun risco_b_tv_centro_sul_por_id_exato_continua_preservada() {
        val ents = listOf(
            ent("globo_tv_verdes_mares.br", "Globo TV Verdes Mares"),
            ent("tv_centro_sul_br", "Nome Ligeiramente Diferente", streams = 1)
        )
        val byNum = ChannelNumbering.resolveFixedEntities(ents, "CE").first.associateBy { it.number }
        // Identificação prioritária por ID, independentemente do nome exibido.
        assertEquals("tv_centro_sul_br", byNum[8]?.channelId)
    }
}
