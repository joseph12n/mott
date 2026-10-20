// Pure hub DTO <-> app view-model mapping functions.

import type {
  HubBranding,
  HubExpense,
  HubOrder,
  HubProduct,
  HubSnapshot,
  HubSupplier,
  HubTable,
  HubToday,
} from './model.ts'
import type {
  BrandingDto,
  ExpenseDto,
  ProductDto,
  SaleDto,
  SupplierDto,
  TabDto,
  TableDto,
  TodayResponse,
} from './types.ts'

export function fromCents(cents: number): number {
  if (!Number.isFinite(cents)) return 0
  return cents / 100
}

export function toCents(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.round(value * 100)
}

export function mapProducts(
  dtos: ProductDto[],
  existingCategories?: Record<string, string>,
): HubProduct[] {
  return dtos.map((dto) => ({
    id: dto.id,
    name: dto.name,
    price: fromCents(dto.price_cents),
    available: dto.available,
    category: existingCategories?.[dto.id] || 'Carta',
  }))
}

export function mapTables(tables: TableDto[], tabs: TabDto[]): HubTable[] {
  const tabByTable = new Map<string, TabDto>()
  for (const tab of tabs) {
    if (tab.status === 'open' || !tabByTable.has(tab.table_id)) {
      tabByTable.set(tab.table_id, tab)
    }
  }
  return tables.map((table) => {
    const tab = tabByTable.get(table.id)
    return {
      id: table.id,
      name: table.label,
      open: Boolean(table.occupied),
      lines: tab
        ? (tab.items ?? []).map((item) => ({
            productId: item.product_id,
            qty: item.qty,
          }))
        : [],
      tabId: tab ? tab.id : null,
    }
  })
}

export function mapSales(sales: SaleDto[], tableNames: Record<string, string>): HubOrder[] {
  return sales.map((sale) => {
    const parsed = new Date(sale.closed_at)
    const hour = Number.isNaN(parsed.getTime()) ? 0 : parsed.getHours()
    return {
      id: sale.id,
      table: tableNames[sale.table_id] || sale.table_id,
      closedAt: sale.closed_at,
      hour,
      items: (sale.items ?? []).map((item) => ({
        name: item.name,
        qty: item.qty,
        price: fromCents(item.unit_price_cents),
      })),
      total: fromCents(sale.total_cents),
    }
  })
}

export function mapExpenses(dtos: ExpenseDto[]): HubExpense[] {
  return dtos.map((dto) => ({
    id: dto.id,
    concept: dto.description,
    amount: fromCents(dto.qty * dto.cost_cents),
    date: dto.date,
  }))
}

export function mapSuppliers(dtos: SupplierDto[]): HubSupplier[] {
  return dtos.map((dto) => ({
    id: dto.id,
    name: dto.name,
    phone: dto.phone ?? '',
    note: dto.note ?? '',
  }))
}

export function mapBranding(baseUrl: string, dto: BrandingDto): HubBranding {
  const cleanBase = baseUrl.trim().replace(/\/+$/, '')
  return {
    name: dto.shop_name,
    accent: dto.primary,
    accent2: dto.accent,
    logoUrl: dto.has_logo
      ? `${cleanBase}/api/branding/logo?v=${encodeURIComponent(dto.updated_at)}`
      : null,
  }
}

export function mapToday(dto: TodayResponse): HubToday {
  return {
    date: dto.date,
    count: dto.count,
    totalCents: dto.total_cents,
  }
}

export function buildSnapshot(input: {
  products: ProductDto[]
  tables: TableDto[]
  tabs: TabDto[]
  sales: SaleDto[]
  expenses: ExpenseDto[]
  suppliers: SupplierDto[]
  branding: BrandingDto
  today: TodayResponse
  baseUrl: string
  categories?: Record<string, string>
}): HubSnapshot {
  const tableNames: Record<string, string> = {}
  for (const t of input.tables) {
    tableNames[t.id] = t.label
  }
  return {
    products: mapProducts(input.products, input.categories),
    tables: mapTables(input.tables, input.tabs),
    orders: mapSales(input.sales, tableNames),
    expenses: mapExpenses(input.expenses),
    suppliers: mapSuppliers(input.suppliers),
    branding: mapBranding(input.baseUrl, input.branding),
    today: mapToday(input.today),
  }
}
