@file:Suppress("TooManyFunctions", "MaxLineLength", "MagicNumber", "TooGenericExceptionCaught")
package com.mmgb.snake

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsParams.Product
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class BillingManager(private val context: Context) : PurchasesUpdatedListener {
    companion object {
        const val PRODUCT_REMOVE_ADS = "remove_ads"
        const val PRODUCT_BOOSTER_PACK = "booster_pack"
        const val PRODUCT_SUPER_BOOST = "super_boost"
        private const val BOOSTERS_PER_PACK = 3
        // Coins awarded per product (adjust for economy balance)
        private const val COINS_PER_BOOSTER_PACK = 150
        private const val COINS_PER_SUPER_BOOST = 500
        const val BOOSTER_START_LENGTH = "booster_start_length"
        const val BOOSTER_SCORE_MULT = "booster_score_mult"
        const val BOOSTER_EXTRA_TIME = "booster_extra_time"
        const val BOOSTER_SUPER_SHIELD = "super_shield"
        private val STANDARD_BOOSTERS = listOf(BOOSTER_START_LENGTH, BOOSTER_SCORE_MULT, BOOSTER_EXTRA_TIME)
        // Pure awarding logic (pack -> 3 random standard boosters, super -> 1 shield) for delta counts
        internal fun awardForProducts(packs: Int, supers: Int, rnd: Random = Random.Default): Map<String, Int> {
            if (packs <= 0 && supers <= 0) return emptyMap()
            val result = mutableMapOf<String, Int>()
            repeat(packs * BOOSTERS_PER_PACK) { val pick = STANDARD_BOOSTERS[rnd.nextInt(STANDARD_BOOSTERS.size)]; result[pick] = (result[pick] ?: 0) + 1 }
            repeat(supers) { result[BOOSTER_SUPER_SHIELD] = (result[BOOSTER_SUPER_SHIELD] ?: 0) + 1 }
            return result
        }
    }

    private val tag = "BillingManager"
    private val scope = CoroutineScope(Dispatchers.Main)

    // Purchase events for UI feedback
    sealed class BillingEvent {
        data class Success(val products: List<String>, val coinsAdded: Int) : BillingEvent()
        data class Pending(val products: List<String>) : BillingEvent()
        data class Failure(val code: Int, val message: String?) : BillingEvent()
        object Canceled : BillingEvent()
    }
    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BillingEvent> = _events

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().build())
        .build()

    private var ready = false

    private val _ownedRemoveAds = MutableStateFlow(false)
    val ownedRemoveAds: StateFlow<Boolean> = _ownedRemoveAds

    private val _boosterCredits = MutableStateFlow(0)
    val boosterCredits: StateFlow<Int> = _boosterCredits

    private val _coins = MutableStateFlow(loadCoinsPref())
    val coins: StateFlow<Int> = _coins

    init { startConnection() }

    private fun startConnection() {
        if (billingClient.isReady) {
            ready = true
            queryExistingPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    ready = true
                    queryExistingPurchases()
                } else {
                    Log.w(tag, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                ready = false
                Log.w(tag, "Billing service disconnected")
            }
        })
    }

    internal fun isReady(): Boolean = ready

    internal fun queryProductDetails(productIds: List<String>, callback: (List<ProductDetails>) -> Unit) {
        if (!ready) startConnection()
        val productList = productIds.map { id ->
            Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        scope.launch {
            billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    callback(result.productDetailsList)
                } else {
                    Log.w(tag, "queryProductDetails failed: ${billingResult.debugMessage}")
                    callback(emptyList())
                }
            }
        }
    }

    internal fun launchPurchaseFlow(activity: Activity, product: ProductDetails) {
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        Log.d(tag, "launchBillingFlow result=${result.responseCode}")
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(tag, "Purchase canceled")
                _events.tryEmit(BillingEvent.Canceled)
            }
            else -> {
                Log.w(tag, "Purchase failed: ${billingResult.debugMessage}")
                _events.tryEmit(BillingEvent.Failure(billingResult.responseCode, billingResult.debugMessage))
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                _events.tryEmit(BillingEvent.Pending(purchase.products))
            }
            Purchase.PurchaseState.PURCHASED -> {
                val products = purchase.products
                val hasConsumable = products.any { it == PRODUCT_BOOSTER_PACK || it == PRODUCT_SUPER_BOOST }
                val coinsAdded = grantProducts(products)
                // For non-consumable remove_ads, acknowledge; for consumables, consume to allow repurchase
                if (hasConsumable) {
                    consumePurchase(purchase)
                } else if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                _events.tryEmit(BillingEvent.Success(products, coinsAdded))
            }
            else -> { /* UNSPECIFIED_STATE: ignore */ }
        }
    }

    private fun grantProducts(products: List<String>): Int {
        if (products.isEmpty()) return 0
        if (PRODUCT_REMOVE_ADS in products) {
            _ownedRemoveAds.value = true
            saveRemoveAds(context, true)
        }
        val packs = products.count { it == PRODUCT_BOOSTER_PACK }
        val supers = products.count { it == PRODUCT_SUPER_BOOST }
        if (packs == 0 && supers == 0) return 0
        // Load processed counts to avoid double-award if purchase objects replay
        val (procPacks, procSupers) = loadProcessedCounts()
        val newPacks = packs // single purchase list corresponds to one purchase -> treat all as new
        val newSupers = supers
        val awardMap = awardForProducts(newPacks, newSupers, Random.Default)
        if (awardMap.isNotEmpty()) {
            applyBoosterInventoryAwards(awardMap)
            val awardedCount = awardMap.values.sum()
            val total = _boosterCredits.value + awardedCount
            _boosterCredits.value = total
            saveBoosterCredits(context, total)
            // NEW: grant coins for purchased products
            val coinsAdd = packs * COINS_PER_BOOSTER_PACK + supers * COINS_PER_SUPER_BOOST
            if (coinsAdd > 0) adjustCoins(coinsAdd)
            saveProcessedCounts(procPacks + newPacks, procSupers + newSupers)
            return coinsAdd
        }
        return 0
    }

    private fun consumePurchase(purchase: Purchase) {
        try {
            val params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.consumeAsync(params, ConsumeResponseListener { result, token ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(tag, "Consume failed: ${result.debugMessage}")
                } else {
                    Log.d(tag, "Consumed purchase token=$token")
                }
            })
        } catch (t: Throwable) {
            Log.w(tag, "Consume exception", t)
        }
    }

    private fun applyBoosterInventoryAwards(additions: Map<String, Int>) {
        if (additions.isEmpty()) return
        val prefs = context.getSharedPreferences("snake", Context.MODE_PRIVATE)
        val raw = prefs.getString(PrefKeys.BOOSTERS, null)
        val current: MutableMap<String, Int> = if (raw.isNullOrBlank()) mutableMapOf() else raw.split(';').mapNotNull {
            if (!it.contains('=')) return@mapNotNull null
            val parts = it.split('=')
            val id = parts.getOrNull(0)?.trim().orEmpty()
            val count = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (id.isNotBlank() && count > 0) id to count else null
        }.toMap().toMutableMap()
        additions.forEach { (k, v) -> current[k] = (current[k] ?: 0) + v }
        val newRaw = current.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit().putString(PrefKeys.BOOSTERS, newRaw).apply()
    }

    private fun adjustCoins(delta: Int) {
        val prefs = context.getSharedPreferences("snake", Context.MODE_PRIVATE)
        val current = prefs.getInt(PrefKeys.COINS, 0)
        val updated = current + delta
        prefs.edit { putInt(PrefKeys.COINS, updated) }
        _coins.value = updated
        Log.d(tag, "Coins adjusted +$delta => $updated")
    }

    private fun loadCoinsPref(): Int = context.getSharedPreferences("snake", Context.MODE_PRIVATE).getInt(PrefKeys.COINS, 0)

    fun queryExistingPurchases() {
        if (!ready) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val ownedRemove = purchases.any { it.products.contains(PRODUCT_REMOVE_ADS) }
                _ownedRemoveAds.value = ownedRemove
                saveRemoveAds(context, ownedRemove)
                val totalPacks = purchases.sumOf { it.products.count { sku -> sku == PRODUCT_BOOSTER_PACK } }
                val totalSupers = purchases.sumOf { it.products.count { sku -> sku == PRODUCT_SUPER_BOOST } }
                val (procPacks, procSupers) = loadProcessedCounts()
                val deltaPacks = (totalPacks - procPacks).coerceAtLeast(0)
                val deltaSupers = (totalSupers - procSupers).coerceAtLeast(0)
                if (deltaPacks > 0 || deltaSupers > 0) {
                    val awardMap = awardForProducts(deltaPacks, deltaSupers, Random(42))
                    applyBoosterInventoryAwards(awardMap)
                    val awardedCount = awardMap.values.sum()
                    val newCredits = _boosterCredits.value + awardedCount
                    _boosterCredits.value = newCredits
                    saveBoosterCredits(context, newCredits)
                    // Coins for outstanding (delta) purchases
                    val coinsAdd = deltaPacks * COINS_PER_BOOSTER_PACK + deltaSupers * COINS_PER_SUPER_BOOST
                    if (coinsAdd > 0) adjustCoins(coinsAdd)
                    saveProcessedCounts(procPacks + deltaPacks, procSupers + deltaSupers)
                } else {
                    _boosterCredits.value = loadBoosterCredits(context)
                }
            }
        }
    }

    private fun saveRemoveAds(ctx: Context, value: Boolean) {
        ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putBoolean(PrefKeys.REMOVE_ADS, value) }
    }

    private fun saveBoosterCredits(ctx: Context, value: Int) {
        ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).edit { putInt(PrefKeys.BOOSTER_CREDITS, value) }
    }

    private fun loadBoosterCredits(ctx: Context): Int =
        ctx.getSharedPreferences("snake", Context.MODE_PRIVATE).getInt(PrefKeys.BOOSTER_CREDITS, 0)

    internal fun consumeBooster(units: Int = 1): Boolean {
        val current = _boosterCredits.value
        if (current < units) return false
        val newValue = current - units
        _boosterCredits.value = newValue
        saveBoosterCredits(context, newValue)
        return true
    }

    // Move acknowledgePurchase near top to quiet analyzer false-positive
    private fun acknowledgePurchase(purchase: Purchase) {
        try {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(tag, "Acknowledge failed: ${result.debugMessage}")
                }
            }
        } catch (t: Throwable) { Log.w(tag, "Acknowledge exception", t) }
    }

    private fun loadProcessedCounts(): Pair<Int, Int> {
        val p = context.getSharedPreferences("snake", Context.MODE_PRIVATE)
        return p.getInt(PrefKeys.PROC_PACKS, 0) to p.getInt(PrefKeys.PROC_SUPERS, 0)
    }

    private fun saveProcessedCounts(packs: Int, supers: Int) {
        val p = context.getSharedPreferences("snake", Context.MODE_PRIVATE)
        p.edit { putInt(PrefKeys.PROC_PACKS, packs); putInt(PrefKeys.PROC_SUPERS, supers) }
    }

    fun endConnection() {
        try { billingClient.endConnection() } catch (_: Throwable) {}
    }
}
