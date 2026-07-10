package com.sharedshoppinglists.app.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

// --- Enums ---

enum class AuthProvider { EMAIL, GOOGLE }

enum class CardType { CREDIT, LOYALTY }

enum class ChangeType { ADDED, UPDATED, REMOVED, PURCHASED }

data class CustomCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val sortOrder: Int
)

// --- Domain Entities ---

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val authProvider: AuthProvider
)

data class ShoppingList(
    val id: String,
    val name: String,
    val ownerId: String,
    val members: List<String>,
    val isShared: Boolean,
    val emoji: String = "",
    val color: String = "",
    val createdAt: Instant,
    val updatedAt: Instant
)

data class Product(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val categoryId: String = "",
    val categoryName: String = "Otros",
    val categoryEmoji: String = "📦",
    val emoji: String = "",
    val color: String = "",
    val preferredBrand: String = "",
    val isPurchased: Boolean,
    val lastModifiedBy: String,
    val lastModifiedAt: Instant
)

data class KnownProduct(
    val id: String,
    val name: String,
    val emoji: String,
    val categoryId: String,
    val defaultUnit: String,
    val timesUsed: Int = 0
)

data class DiscountCard(
    val id: String,
    val userId: String,
    val type: CardType,
    val issuer: String,
    val applicableSupermarkets: Map<String, Discount>,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?
)

data class Discount(
    val percentage: BigDecimal?,
    val fixedAmount: BigDecimal?,
    val minimumPurchase: BigDecimal?
)

data class Supermarket(
    val id: String,
    val chainId: String,
    val name: String,
    val location: Location,
    val distanceKm: Double
)

data class Location(
    val latitude: Double,
    val longitude: Double
)

// --- Shared List Events ---

data class ListChangeEvent(
    val productId: String,
    val changeType: ChangeType,
    val modifiedBy: String,
    val timestamp: Long
)

data class EditingStatus(
    val productId: String,
    val userId: String,
    val userName: String,
    val isEditing: Boolean
)

data class PendingInvitation(
    val id: String,
    val listId: String,
    val listName: String,
    val inviteeEmail: String
)

// --- Price Comparison Models ---

data class PriceComparisonResult(
    val productPrices: List<ProductPriceComparison>,
    val supermarkets: List<Supermarket>
)

data class ProductPriceComparison(
    val product: Product,
    val prices: Map<String, PriceEntry?> // supermarketId -> PriceEntry (null = no disponible)
)

data class PriceEntry(
    val price: BigDecimal,
    val lastUpdated: Instant
)

// --- Optimal Cost & Split Purchase Models ---

data class OptimalCostResult(
    val rankings: List<SupermarketCost>,
    val savings: BigDecimal,
    val excludedSupermarkets: Map<String, List<String>> // supermarketId -> productIds faltantes
)

data class SupermarketCost(
    val supermarket: Supermarket,
    val totalOriginal: BigDecimal,
    val totalWithDiscount: BigDecimal,
    val appliedDiscounts: List<AppliedDiscount>
)

data class AppliedDiscount(
    val cardId: String,
    val cardIssuer: String,
    val discountPercentage: BigDecimal?,
    val discountAmount: BigDecimal?,
    val savedAmount: BigDecimal
)

data class SplitPurchaseResult(
    val assignments: List<ProductAssignment>,
    val totalCost: BigDecimal,
    val savingsVsBestSingle: BigDecimal
)

data class ProductAssignment(
    val product: Product,
    val supermarket: Supermarket,
    val price: BigDecimal,
    val discountApplied: AppliedDiscount?
)

// --- Manual Price Comparator Models ---

data class MySupermarket(
    val id: String,
    val userId: String,
    val name: String,
    val address: String
)

data class ManualPrice(
    val id: String,
    val userId: String,
    val supermarketId: String,
    val productName: String,
    val price: Double,
    val updatedAt: Instant
)

data class AffinityProgram(
    val id: String,
    val userId: String,
    val name: String,
    val supermarketId: String,
    val discountPercentage: Double,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?
)

data class ComparisonResult(
    val supermarket: MySupermarket,
    val subtotal: Double,
    val cardDiscount: Double,
    val affinityDiscount: Double,
    val total: Double,
    val missingProducts: List<String>,
    val appliedCardName: String?,
    val appliedAffinityName: String?
)

// --- Smart Discount Optimizer Models ---

data class MedioPago(
    val id: Int,
    val nombre: String,
    val tipo: String, // "banco", "billetera_digital", "club_beneficios"
    val nombreDisplay: String,
    val tarjetasDisponibles: List<String>?,
    val activo: Boolean = true
)

data class OpcionProducto(
    val nombre: String,
    val marca: String,
    val presentacion: String,
    val preciosPorCadena: List<PrecioCadena>
)

data class PrecioCadena(
    val cadena: String,
    val productoId: String,
    val precio: Double
)

data class ProductoOptimizar(
    val productoId: String?,
    val nombre: String?,
    val cantidad: Int,
    val unidad: String = ""
)

data class OptimizarRequest(
    val productos: List<ProductoOptimizar>,
    val mediosPagoIds: List<Int>,
    val tarjetasSeleccionadas: Map<String, List<String>>,
    val diaSemana: String,
    val cadenas: List<String> = emptyList()
)

data class OptimizationResult(
    val cadenaRecomendada: String?,
    val totalOriginal: Double,
    val totalFinal: Double,
    val ahorroTotal: Double,
    val ahorroPorcentaje: Double,
    val distribucionPagos: List<PagoDistribuido>,
    val productosFaltantes: List<String>,
    val productosSeleccionados: List<ProductoSeleccionado>,
    val rankingCadenas: List<CadenaRanking>
)

data class ProductoSeleccionado(
    val nombre: String,
    val marca: String,
    val precio: Double,
    val cantidad: Int,
    val busqueda: String
)

data class PagoDistribuido(
    val medioPago: String,
    val tarjeta: String?,
    val monto: Double,
    val descuentoPct: Double,
    val ahorro: Double,
    val topeAplicado: Boolean
)

data class CadenaRanking(
    val cadena: String,
    val totalOriginal: Double = 0.0,
    val totalFinal: Double,
    val ahorro: Double,
    val ahorroPorcentaje: Double = 0.0,
    val distribucionPagos: List<PagoDistribuido> = emptyList(),
    val productosFaltantes: List<String> = emptyList(),
    val productosSeleccionados: List<ProductoSeleccionado> = emptyList()
)
