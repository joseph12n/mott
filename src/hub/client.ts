// Pure fetch client for the mitt PC hub LAN API.

import { normalizeBaseUrl } from './pairing.ts'
import type {
  AddItemRequest,
  BrandingDto,
  BrandingPatchRequest,
  ErrorEnvelope,
  ExpenseCreateRequest,
  ExpenseDto,
  ExpensesResponse,
  HealthResponse,
  OpenTabRequest,
  OpenTabsResponse,
  PairingResponse,
  ProductCreateRequest,
  ProductDto,
  ProductPatchRequest,
  ProductsResponse,
  SaleDto,
  SalesResponse,
  SupplierCreateRequest,
  SupplierDto,
  SupplierPatchRequest,
  SuppliersResponse,
  TabDto,
  TableCreateRequest,
  TableDto,
  TablesResponse,
  TodayResponse,
} from './types.ts'

export class HubError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'HubError'
    this.status = status
    this.code = code
  }
}

export type HubClient = {
  readonly baseUrl: string
  health(): Promise<HealthResponse>
  pairing(): Promise<PairingResponse>
  listProducts(available?: boolean): Promise<ProductDto[]>
  createProduct(body: ProductCreateRequest): Promise<ProductDto>
  patchProduct(id: string, body: ProductPatchRequest): Promise<ProductDto>
  listTables(): Promise<TableDto[]>
  createTable(body: TableCreateRequest): Promise<TableDto>
  deleteTable(id: string): Promise<void>
  openTabs(): Promise<TabDto[]>
  openTab(body: OpenTabRequest): Promise<TabDto>
  addItem(tabId: string, body: AddItemRequest): Promise<TabDto>
  closeTab(tabId: string): Promise<SaleDto>
  listSales(limit?: number): Promise<SaleDto[]>
  salesToday(): Promise<TodayResponse>
  listExpenses(): Promise<ExpenseDto[]>
  createExpense(body: ExpenseCreateRequest): Promise<ExpenseDto>
  listSuppliers(): Promise<SupplierDto[]>
  createSupplier(body: SupplierCreateRequest): Promise<SupplierDto>
  patchSupplier(id: string, body: SupplierPatchRequest): Promise<SupplierDto>
  deleteSupplier(id: string): Promise<void>
  getBranding(): Promise<BrandingDto>
  patchBranding(body: BrandingPatchRequest): Promise<BrandingDto>
}

const PUBLIC_GET_PATHS = new Set(['/api/health', '/api/branding', '/api/branding/logo'])

function isPublicRequest(method: string, path: string): boolean {
  if (method !== 'GET') return false
  const cleanPath = path.split('?')[0]
  return PUBLIC_GET_PATHS.has(cleanPath)
}

export function createHubClient(options: {
  baseUrl: string
  token: string
  fetchImpl?: typeof fetch
  timeoutMs?: number
}): HubClient {
  const baseUrl = normalizeBaseUrl(options.baseUrl)
  const token = options.token.trim()
  const fetchImpl = options.fetchImpl ?? fetch
  const timeoutMs = options.timeoutMs ?? 4_000

  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const cleanPath = path.startsWith('/') ? path : `/${path}`
    const url = `${baseUrl}${cleanPath}`
    const headers: Record<string, string> = {
      Accept: 'application/json',
    }
    if (!isPublicRequest(method, cleanPath)) {
      headers.Authorization = `Bearer ${token}`
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), timeoutMs)

    let response: Response
    try {
      response = await fetchImpl(url, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      })
    } finally {
      clearTimeout(timer)
    }

    if (response.status === 204) {
      return undefined as T
    }

    const text = await response.text()

    if (!response.ok) {
      let code = `http_${response.status}`
      let message = response.statusText || `HTTP ${response.status}`
      if (text.trim()) {
        try {
          const parsed = JSON.parse(text) as Partial<ErrorEnvelope>
          if (
            parsed &&
            typeof parsed === 'object' &&
            parsed.error &&
            typeof parsed.error.code === 'string' &&
            typeof parsed.error.message === 'string'
          ) {
            code = parsed.error.code
            message = parsed.error.message
          }
        } catch {
          // Keep fallback code and message
        }
      }
      throw new HubError(response.status, code, message)
    }

    if (!text.trim()) {
      return undefined as T
    }

    return JSON.parse(text) as T
  }

  return {
    baseUrl,

    health() {
      return request<HealthResponse>('GET', '/api/health')
    },

    pairing() {
      return request<PairingResponse>('GET', '/api/pairing')
    },

    async listProducts(available?: boolean) {
      const query = available !== undefined ? `?available=${available ? 'true' : 'false'}` : ''
      const res = await request<ProductsResponse>('GET', `/api/products${query}`)
      return res.products ?? []
    },

    createProduct(body: ProductCreateRequest) {
      const payload: ProductCreateRequest = {
        name: body.name,
        price_cents: body.price_cents,
      }
      if (body.available !== undefined) {
        payload.available = body.available
      }
      return request<ProductDto>('POST', '/api/products', payload)
    },

    patchProduct(id: string, body: ProductPatchRequest) {
      const payload: ProductPatchRequest = {}
      if (body.name !== undefined) payload.name = body.name
      if (body.price_cents !== undefined) payload.price_cents = body.price_cents
      if (body.available !== undefined) payload.available = body.available
      return request<ProductDto>('PATCH', `/api/products/${encodeURIComponent(id)}`, payload)
    },

    async listTables() {
      const res = await request<TablesResponse>('GET', '/api/tables')
      return res.tables ?? []
    },

    createTable(body: TableCreateRequest) {
      const payload: TableCreateRequest = { label: body.label }
      return request<TableDto>('POST', '/api/tables', payload)
    },

    deleteTable(id: string) {
      return request<void>('DELETE', `/api/tables/${encodeURIComponent(id)}`)
    },

    async openTabs() {
      const res = await request<OpenTabsResponse>('GET', '/api/tabs/open')
      return res.tabs ?? []
    },

    openTab(body: OpenTabRequest) {
      const payload: OpenTabRequest = { table_id: body.table_id }
      return request<TabDto>('POST', '/api/tabs', payload)
    },

    addItem(tabId: string, body: AddItemRequest) {
      const payload: AddItemRequest = {
        product_id: body.product_id,
        qty: body.qty,
      }
      return request<TabDto>('POST', `/api/tabs/${encodeURIComponent(tabId)}/items`, payload)
    },

    closeTab(tabId: string) {
      return request<SaleDto>('POST', `/api/tabs/${encodeURIComponent(tabId)}/close`)
    },

    async listSales(limit?: number) {
      const query = limit !== undefined ? `?limit=${encodeURIComponent(String(limit))}` : ''
      const res = await request<SalesResponse>('GET', `/api/sales${query}`)
      return res.sales ?? []
    },

    salesToday() {
      return request<TodayResponse>('GET', '/api/sales/today')
    },

    async listExpenses() {
      const res = await request<ExpensesResponse>('GET', '/api/expenses')
      return res.expenses ?? []
    },

    createExpense(body: ExpenseCreateRequest) {
      const payload: ExpenseCreateRequest = {
        description: body.description,
        qty: body.qty,
        cost_cents: body.cost_cents,
      }
      return request<ExpenseDto>('POST', '/api/expenses', payload)
    },

    async listSuppliers() {
      const res = await request<SuppliersResponse>('GET', '/api/suppliers')
      return res.suppliers ?? []
    },

    createSupplier(body: SupplierCreateRequest) {
      const payload: SupplierCreateRequest = {
        name: body.name,
      }
      if (body.phone !== undefined) payload.phone = body.phone
      if (body.note !== undefined) payload.note = body.note
      return request<SupplierDto>('POST', '/api/suppliers', payload)
    },

    patchSupplier(id: string, body: SupplierPatchRequest) {
      const payload: SupplierPatchRequest = {}
      if (body.name !== undefined) payload.name = body.name
      if (body.phone !== undefined) payload.phone = body.phone
      if (body.note !== undefined) payload.note = body.note
      return request<SupplierDto>('PATCH', `/api/suppliers/${encodeURIComponent(id)}`, payload)
    },

    deleteSupplier(id: string) {
      return request<void>('DELETE', `/api/suppliers/${encodeURIComponent(id)}`)
    },

    getBranding() {
      return request<BrandingDto>('GET', '/api/branding')
    },

    patchBranding(body: BrandingPatchRequest) {
      const payload: BrandingPatchRequest = {}
      if (body.shop_name !== undefined) payload.shop_name = body.shop_name
      if (body.primary !== undefined) payload.primary = body.primary
      if (body.accent !== undefined) payload.accent = body.accent
      if (body.background !== undefined) payload.background = body.background
      if ('logo_data_url' in body && body.logo_data_url !== undefined) {
        payload.logo_data_url = body.logo_data_url
      }
      return request<BrandingDto>('PATCH', '/api/branding', payload)
    },
  }
}
