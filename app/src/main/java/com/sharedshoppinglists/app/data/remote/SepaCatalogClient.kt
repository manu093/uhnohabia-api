package com.sharedshoppinglists.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SepaProductInfo(
    val id: String,
    val nombre: String,
    val marca: String,
    val presentacion: String,
    val cadena: String,
    val precio: Double,
    val precioLista: Double,
    val imagen: String
)

data class CatalogStatus(
    val lastRun: String,
    val totalProducts: Int,
    val totalCadenas: Int
)

data class PromoBancaria(
    val id: Int,
    val cadena: String,
    val banco: String,
    val tarjeta: String,
    val descuentoPct: Double,
    val diaSemana: String,
    val topeReintegro: Double,
    val condiciones: String
)

interface SepaCatalogClient {
    suspend fun searchProducts(query: String, marca: String? = null, cadena: String? = null): List<SepaProductInfo>
    suspend fun getCadenas(): List<String>
    suspend fun getMarcas(query: String? = null): List<String>
    suspend fun getPromos(cadena: String? = null): List<PromoBancaria>
    suspend fun getStatus(): CatalogStatus?
    suspend fun getMediosPago(): List<com.sharedshoppinglists.app.domain.model.MedioPago>
    suspend fun buscarOpciones(query: String): List<com.sharedshoppinglists.app.domain.model.OpcionProducto>
    suspend fun optimizar(request: com.sharedshoppinglists.app.domain.model.OptimizarRequest): com.sharedshoppinglists.app.domain.model.OptimizationResult?
}

@Singleton
class SepaCatalogClientImpl @Inject constructor(
    private val httpClient: OkHttpClient
) : SepaCatalogClient {

    private val baseUrl = "https://colonial-albertine-pepin-5207cd9b.koyeb.app"

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "UhNoHabia-Android/1.0").build()
        val resp = httpClient.newCall(req).execute()
        if (resp.isSuccessful) resp.body?.string() else null
    } catch (_: Exception) { null }

    override suspend fun searchProducts(query: String, marca: String?, cadena: String?): List<SepaProductInfo> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/catalog/productos".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .apply {
                if (!marca.isNullOrBlank()) addQueryParameter("marca", marca)
                if (!cadena.isNullOrBlank()) addQueryParameter("cadena", cadena)
            }
            .addQueryParameter("limit", "50")
            .build().toString()
        val body = get(url) ?: return@withContext emptyList()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SepaProductInfo(
                id = o.getString("id"),
                nombre = o.getString("nombre"),
                marca = o.optString("marca", ""),
                presentacion = o.optString("presentacion", ""),
                cadena = o.optString("cadena", ""),
                precio = o.optDouble("precio", 0.0),
                precioLista = o.optDouble("precioLista", 0.0),
                imagen = o.optString("imagen", "")
            )
        }
    }

    override suspend fun getCadenas(): List<String> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/catalog/cadenas") ?: return@withContext emptyList()
        val arr = JSONArray(body)
        (0 until arr.length()).map { arr.getString(it) }
    }

    override suspend fun getMarcas(query: String?): List<String> = withContext(Dispatchers.IO) {
        val url = if (query != null) "$baseUrl/catalog/marcas?q=${java.net.URLEncoder.encode(query, "UTF-8")}" else "$baseUrl/catalog/marcas"
        val body = get(url) ?: return@withContext emptyList()
        val arr = JSONArray(body)
        (0 until arr.length()).map { arr.getString(it) }
    }

    override suspend fun getPromos(cadena: String?): List<PromoBancaria> = withContext(Dispatchers.IO) {
        val url = if (cadena != null) "$baseUrl/catalog/promos?cadena=${java.net.URLEncoder.encode(cadena, "UTF-8")}" else "$baseUrl/catalog/promos"
        val body = get(url) ?: return@withContext emptyList()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PromoBancaria(
                id = o.optInt("id", 0), cadena = o.optString("cadena", ""),
                banco = o.optString("banco", ""), tarjeta = o.optString("tarjeta", ""),
                descuentoPct = o.optDouble("descuentoPct", 0.0),
                diaSemana = o.optString("diaSemana", ""),
                topeReintegro = o.optDouble("topeReintegro", 0.0),
                condiciones = o.optString("condiciones", "")
            )
        }
    }

    override suspend fun getStatus(): CatalogStatus? = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/catalog/status") ?: return@withContext null
        val o = JSONObject(body)
        CatalogStatus(o.optString("lastRun", ""), o.optInt("totalProducts", 0), o.optInt("totalCadenas", 0))
    }

    override suspend fun getMediosPago(): List<com.sharedshoppinglists.app.domain.model.MedioPago> = withContext(Dispatchers.IO) {
        val body = get("$baseUrl/catalog/medios_pago") ?: return@withContext emptyList()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tarjetas = o.optJSONArray("tarjetasDisponibles")
            com.sharedshoppinglists.app.domain.model.MedioPago(
                id = o.getInt("id"), nombre = o.getString("nombre"),
                tipo = o.getString("tipo"), nombreDisplay = o.getString("nombreDisplay"),
                tarjetasDisponibles = if (tarjetas != null) (0 until tarjetas.length()).map { tarjetas.getString(it) } else null
            )
        }
    }

    override suspend fun buscarOpciones(query: String): List<com.sharedshoppinglists.app.domain.model.OpcionProducto> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/catalog/buscar_opciones?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val body = get(url) ?: return@withContext emptyList()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val precios = o.getJSONArray("preciosPorCadena")
            com.sharedshoppinglists.app.domain.model.OpcionProducto(
                nombre = o.getString("nombre"), marca = o.optString("marca", ""),
                presentacion = o.optString("presentacion", ""),
                preciosPorCadena = (0 until precios.length()).map { j ->
                    val p = precios.getJSONObject(j)
                    com.sharedshoppinglists.app.domain.model.PrecioCadena(p.getString("cadena"), p.getString("productoId"), p.getDouble("precio"))
                }
            )
        }
    }

    override suspend fun optimizar(request: com.sharedshoppinglists.app.domain.model.OptimizarRequest): com.sharedshoppinglists.app.domain.model.OptimizationResult? = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("productos", org.json.JSONArray().apply {
                    request.productos.forEach { p ->
                        put(JSONObject().apply {
                            p.productoId?.let { put("producto_id", it) }
                            p.nombre?.let { put("nombre", it) }
                            put("cantidad", p.cantidad)
                            if (p.unidad.isNotBlank()) put("unidad", p.unidad)
                        })
                    }
                })
                put("medios_pago_ids", org.json.JSONArray(request.mediosPagoIds))
                put("tarjetas_seleccionadas", JSONObject(request.tarjetasSeleccionadas.mapValues { org.json.JSONArray(it.value) }))
                put("dia_semana", request.diaSemana)
                if (request.cadenas.isNotEmpty()) put("cadenas", org.json.JSONArray(request.cadenas))
            }
            val reqBody = okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), jsonBody.toString())
            val req = Request.Builder().url("$baseUrl/catalog/optimizar").post(reqBody)
                .header("User-Agent", "UhNoHabia-Android/1.0").header("Content-Type", "application/json").build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string() ?: return@withContext null
            val o = JSONObject(body)
            val pagos = o.optJSONArray("distribucionPagos") ?: org.json.JSONArray()
            val ranking = o.optJSONArray("rankingCadenas") ?: org.json.JSONArray()
            val faltantes = o.optJSONArray("productosFaltantes") ?: org.json.JSONArray()
            val seleccionados = o.optJSONArray("productosSeleccionados") ?: org.json.JSONArray()
            com.sharedshoppinglists.app.domain.model.OptimizationResult(
                cadenaRecomendada = o.optString("cadenaRecomendada", null),
                totalOriginal = o.optDouble("totalOriginal", 0.0),
                totalFinal = o.optDouble("totalFinal", 0.0),
                ahorroTotal = o.optDouble("ahorroTotal", 0.0),
                ahorroPorcentaje = o.optDouble("ahorroPorcentaje", 0.0),
                distribucionPagos = (0 until pagos.length()).map { i ->
                    val p = pagos.getJSONObject(i)
                    com.sharedshoppinglists.app.domain.model.PagoDistribuido(
                        p.getString("medioPago"), p.optString("tarjeta", null),
                        p.getDouble("monto"), p.getDouble("descuentoPct"),
                        p.getDouble("ahorro"), p.getBoolean("topeAplicado"))
                },
                productosFaltantes = (0 until faltantes.length()).map { faltantes.getString(it) },
                productosSeleccionados = (0 until seleccionados.length()).map { i ->
                    val s = seleccionados.getJSONObject(i)
                    com.sharedshoppinglists.app.domain.model.ProductoSeleccionado(
                        s.getString("nombre"), s.optString("marca", ""),
                        s.getDouble("precio"), s.optInt("cantidad", 1), s.optString("busqueda", ""))
                },
                rankingCadenas = (0 until ranking.length()).map { i ->
                    val r = ranking.getJSONObject(i)
                    val rPagos = r.optJSONArray("distribucionPagos") ?: org.json.JSONArray()
                    val rFalt = r.optJSONArray("productosFaltantes") ?: org.json.JSONArray()
                    val rSel = r.optJSONArray("productosSeleccionados") ?: org.json.JSONArray()
                    com.sharedshoppinglists.app.domain.model.CadenaRanking(
                        cadena = r.getString("cadena"),
                        totalOriginal = r.optDouble("totalOriginal", 0.0),
                        totalFinal = r.getDouble("totalFinal"),
                        ahorro = r.getDouble("ahorro"),
                        ahorroPorcentaje = r.optDouble("ahorroPorcentaje", 0.0),
                        distribucionPagos = (0 until rPagos.length()).map { j ->
                            val p2 = rPagos.getJSONObject(j)
                            com.sharedshoppinglists.app.domain.model.PagoDistribuido(
                                p2.getString("medioPago"), p2.optString("tarjeta", null),
                                p2.getDouble("monto"), p2.getDouble("descuentoPct"),
                                p2.getDouble("ahorro"), p2.getBoolean("topeAplicado"))
                        },
                        productosFaltantes = (0 until rFalt.length()).map { j -> rFalt.getString(j) },
                        productosSeleccionados = (0 until rSel.length()).map { j ->
                            val s2 = rSel.getJSONObject(j)
                            com.sharedshoppinglists.app.domain.model.ProductoSeleccionado(
                                s2.getString("nombre"), s2.getString("marca"),
                                s2.getDouble("precio"), s2.optInt("cantidad", 1), s2.optString("busqueda", ""))
                        }
                    )
                }
            )
        } catch (_: Exception) { null }
    }
}
