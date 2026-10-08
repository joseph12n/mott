package dev.mott.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlinx.serialization.json.JsonObject

// Retrofit surface for the mitt LAN HTTP API. Every call returns
// Response<T> so the sync layer can read status codes: 2xx means the op
// is done, 4xx means the payload is poison and must be dropped, 5xx/IO
// means retry later. Suspend functions; safe to call from a worker.
interface MittApi {
    @GET("api/health")
    suspend fun health(): Response<HealthResponse>

    @GET("api/products")
    suspend fun listProducts(
        @Query("available") available: Boolean? = null,
    ): Response<ProductsResponse>

    @GET("api/tables")
    suspend fun listTables(): Response<TablesResponse>

    @POST("api/tables")
    suspend fun createTable(@Body body: TableCreateRequest): Response<TableDto>

    @DELETE("api/tables/{id}")
    suspend fun deleteTable(@Path("id") id: String): Response<Unit>

    @POST("api/products")
    suspend fun createProduct(@Body body: ProductCreateRequest): Response<ProductDto>

    @PATCH("api/products/{id}")
    suspend fun patchProduct(
        @Path("id") id: String,
        @Body body: ProductPatchRequest,
    ): Response<ProductDto>

    @POST("api/tabs")
    suspend fun openTab(@Body body: OpenTabRequest): Response<TabDto>

    @GET("api/tabs/open")
    suspend fun openTabs(): Response<OpenTabsResponse>

    @POST("api/tabs/{id}/items")
    suspend fun addItem(
        @Path("id") tabId: String,
        @Body body: AddItemRequest,
    ): Response<TabDto>

    @POST("api/tabs/{id}/close")
    suspend fun closeTab(@Path("id") tabId: String): Response<SaleDto>

    @GET("api/sales")
    suspend fun listSales(
        @Query("limit") limit: Int? = null,
    ): Response<SalesResponse>

    @GET("api/sales/today")
    suspend fun salesToday(): Response<TodayResponse>

    @GET("api/expenses")
    suspend fun listExpenses(): Response<ExpensesResponse>

    @POST("api/expenses")
    suspend fun createExpense(@Body body: ExpenseCreateRequest): Response<ExpenseDto>

    @GET("api/suppliers")
    suspend fun listSuppliers(): Response<SuppliersResponse>

    @POST("api/suppliers")
    suspend fun createSupplier(@Body body: SupplierCreateRequest): Response<SupplierDto>

    @PATCH("api/suppliers/{id}")
    suspend fun patchSupplier(
        @Path("id") id: String,
        @Body body: SupplierPatchRequest,
    ): Response<SupplierDto>

    @DELETE("api/suppliers/{id}")
    suspend fun deleteSupplier(@Path("id") id: String): Response<Unit>

    @GET("api/branding")
    suspend fun getBranding(): Response<BrandingResponse>

    // Authed PATCH /api/branding with the merged staging body. The body
    // travels as a JsonObject (never a data class) so an explicit null
    // logo_data_url (clear) survives the wire while an absent key (keep)
    // stays absent: kotlinx explicitNulls=false would drop null data-class
    // fields, but JsonObject entries are explicit JsonElements and keep
    // their JsonNull. Hub merge semantics: nil fields keep stored values.
    @PATCH("api/branding")
    suspend fun patchBranding(@Body body: JsonObject): Response<BrandingResponse>
}
