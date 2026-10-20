// App-facing view models for the hub snapshot.

export interface HubProduct {
  id: string
  name: string
  price: number
  available: boolean
  category: string
}

export interface HubTable {
  id: string
  name: string
  open: boolean
  lines: { productId: string; qty: number }[]
  tabId: string | null
}

export interface HubOrder {
  id: string
  table: string
  closedAt: string
  hour: number
  items: { name: string; qty: number; price: number }[]
  total: number
}

export interface HubExpense {
  id: string
  concept: string
  amount: number
  date: string
}

export interface HubSupplier {
  id: string
  name: string
  phone: string
  note: string
}

export interface HubBranding {
  name: string
  accent: string
  accent2: string
  logoUrl: string | null
}

export interface HubToday {
  date: string
  count: number
  totalCents: number
}

export interface HubSnapshot {
  products: HubProduct[]
  tables: HubTable[]
  orders: HubOrder[]
  expenses: HubExpense[]
  suppliers: HubSupplier[]
  branding: HubBranding
  today: HubToday
}
