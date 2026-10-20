// Wire DTOs matching the mitt PC hub LAN HTTP API (snake_case fields,
// integer cents for money, RFC3339 timestamps).

export interface HealthResponse {
  status: string
}

export interface PairingResponse {
  url: string
  pairing_code: string
}

export interface ProductDto {
  id: string
  name: string
  price_cents: number
  available: boolean
}

export interface ProductsResponse {
  products: ProductDto[]
}

export interface ProductCreateRequest {
  name: string
  price_cents: number
  available?: boolean
}

export interface ProductPatchRequest {
  name?: string
  price_cents?: number
  available?: boolean
}

export interface TableDto {
  id: string
  label: string
  occupied: boolean
}

export interface TablesResponse {
  tables: TableDto[]
}

export interface TableCreateRequest {
  label: string
}

export interface OrderLineDto {
  product_id: string
  name: string
  unit_price_cents: number
  qty: number
  line_total_cents: number
}

export interface TabDto {
  id: string
  table_id: string
  status: string
  opened_at: string
  items: OrderLineDto[]
  total_cents: number
}

export interface OpenTabsResponse {
  tabs: TabDto[]
}

export interface OpenTabRequest {
  table_id: string
}

export interface AddItemRequest {
  product_id: string
  qty: number
}

export interface SaleDto {
  id: string
  table_id: string
  items: OrderLineDto[]
  total_cents: number
  closed_at: string
}

export interface SalesResponse {
  sales: SaleDto[]
}

export interface TodayResponse {
  date: string
  count: number
  total_cents: number
}

export interface ExpenseDto {
  id: string
  description: string
  qty: number
  cost_cents: number
  date: string
}

export interface ExpensesResponse {
  expenses: ExpenseDto[]
}

export interface ExpenseCreateRequest {
  description: string
  qty: number
  cost_cents: number
}

export interface SupplierDto {
  id: string
  name: string
  phone: string
  note: string
}

export interface SuppliersResponse {
  suppliers: SupplierDto[]
}

export interface SupplierCreateRequest {
  name: string
  phone?: string
  note?: string
}

export interface SupplierPatchRequest {
  name?: string
  phone?: string
  note?: string
}

export interface BrandingDto {
  shop_name: string
  primary: string
  accent: string
  background: string
  has_logo: boolean
  updated_at: string
}

export interface BrandingPatchRequest {
  shop_name?: string
  primary?: string
  accent?: string
  background?: string
  logo_data_url?: string | null
}

export interface ErrorEnvelope {
  error: {
    code: string
    message: string
  }
}
