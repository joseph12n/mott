import { useRef, useState } from 'react'
import { Text, useWindowDimensions, View } from 'react-native'

import { money } from '../data'
import { useStore } from '../store'
import { BREAKPOINTS } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Num, Pill, StatusPill, styles as ui } from '../ui'

export default function Mesas() {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [selected, setSelected] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [pick, setPick] = useState<{ productId: string; qty: number }>({ productId: '', qty: 1 })
  const [armedClose, setArmedClose] = useState(false)
  const [armedDelete, setArmedDelete] = useState(false)
  const [queuedNotice, setQueuedNotice] = useState(false)
  const [creatingTable, setCreatingTable] = useState(false)
  const [openingTableId, setOpeningTableId] = useState<string | null>(null)
  const [deletingTableId, setDeletingTableId] = useState<string | null>(null)
  const [addingItem, setAddingItem] = useState(false)
  const [closingTableId, setClosingTableId] = useState<string | null>(null)
  const creatingRef = useRef(false)
  const tableActionRef = useRef(false)

  const price = (id: string) => s.products.find((x) => x.id === id)?.price ?? 0
  const total = (lines: { productId: string; qty: number }[]) =>
    lines.reduce((a, l) => a + l.qty * price(l.productId), 0)

  const openTables = s.tables.filter((t) => t.open)
  const inProgress = openTables.reduce((a, t) => a + total(t.lines), 0)
  const table = s.tables.find((t) => t.id === selected) ?? null
  const split = width >= BREAKPOINTS.twoPane
  const tableBusy = Boolean(
    table &&
      (openingTableId === table.id ||
        deletingTableId === table.id ||
        closingTableId === table.id ||
        addingItem),
  )

  const statusPill = (() => {
    if (s.connection === 'online') {
      return (
        <Pill p={p} tone="brand" dotColor={p.brand}>
          Conectado · datos de la PC
        </Pill>
      )
    }
    if (s.connection === 'unpaired' && !s.pairing) {
      return (
        <Pill p={p} tone="mute" dotColor={p.mute}>
          Datos locales · sin PC
        </Pill>
      )
    }
    return (
      <Pill p={p} tone="sun" dotColor={p.berry}>
        {`Sin conexión · ${s.pending} pendientes`}
      </Pill>
    )
  })()

  const handleCreateTable = async () => {
    if (creatingTable || creatingRef.current) return
    const label = newName.trim()
    if (!label) return
    creatingRef.current = true
    setCreatingTable(true)
    setQueuedNotice(false)
    try {
      const ok = await s.createTable(label)
      if (ok) {
        setNewName('')
      }
    } finally {
      creatingRef.current = false
      setCreatingTable(false)
    }
  }

  const handleOpenTable = async (tableId: string) => {
    if (openingTableId || deletingTableId || tableActionRef.current) return
    tableActionRef.current = true
    setOpeningTableId(tableId)
    setQueuedNotice(false)
    try {
      const ok = await s.openTable(tableId)
      if (!ok && s.connection !== 'online') {
        setQueuedNotice(true)
      }
    } finally {
      tableActionRef.current = false
      setOpeningTableId(null)
    }
  }

  const handleDeleteTable = async (tableId: string) => {
    if (deletingTableId || openingTableId || tableActionRef.current) return
    tableActionRef.current = true
    setDeletingTableId(tableId)
    setQueuedNotice(false)
    try {
      const ok = await s.deleteTable(tableId)
      setArmedDelete(false)
      if (ok) {
        setSelected(null)
      }
    } finally {
      tableActionRef.current = false
      setDeletingTableId(null)
    }
  }

  const handleAddItem = async () => {
    if (!table || !pick.productId || addingItem || closingTableId || tableActionRef.current) return
    tableActionRef.current = true
    setAddingItem(true)
    setQueuedNotice(false)
    try {
      const ok = await s.addItem(table.id, pick.productId, pick.qty)
      if (ok) {
        setPick({ productId: '', qty: 1 })
      } else if (s.connection !== 'online') {
        setPick({ productId: '', qty: 1 })
        setQueuedNotice(true)
      }
    } finally {
      tableActionRef.current = false
      setAddingItem(false)
    }
  }

  const handleCloseTable = async () => {
    if (!table || closingTableId || addingItem || tableActionRef.current) return
    if (!armedClose) {
      setArmedClose(true)
      return
    }
    tableActionRef.current = true
    setClosingTableId(table.id)
    setQueuedNotice(false)
    try {
      const ok = await s.closeTable(table.id)
      setArmedClose(false)
      if (ok || s.connection !== 'online') {
        setSelected(null)
      }
    } finally {
      tableActionRef.current = false
      setClosingTableId(null)
    }
  }

  const list = (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Mesas"
        sub={`${openTables.length} abiertas · ${money(inProgress)} en curso`}
        right={statusPill}
      />

      <Card p={p}>
        <Text style={[ui.label, { color: p.mute }]}>Agregar mesa</Text>
        <View style={{ marginTop: 8, gap: 8 }}>
          <Field p={p} value={newName} onChange={setNewName} placeholder="Nombre de la mesa" />
          <Btn
            p={p}
            label="AGREGAR MESA"
            variant="ghost"
            loading={creatingTable}
            disabled={!newName.trim() || creatingTable}
            onPress={() => void handleCreateTable()}
          />
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
        </View>
      </Card>

      <View style={{ gap: 12, flexDirection: 'row', flexWrap: 'wrap' }}>
        {s.tables.map((t) => (
          <Card
            key={t.id}
            p={p}
            style={{
              minWidth: split ? '48%' : '100%',
              flexGrow: 1,
              borderColor: selected === t.id ? p.brand : p.line,
              borderWidth: t.id === selected ? 2 : 1,
            }}
          >
            <View style={{ gap: 8 }}>
              <Text style={[ui.title, { color: p.ink }]} numberOfLines={1}>
                {t.name}
              </Text>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <StatusPill p={p} on={t.open} onLabel="Ocupada" offLabel="Libre" />
                {!t.synced && s.pairing ? (
                  <Pill p={p} tone="sun" dotColor={p.sunInk}>
                    Pendiente
                  </Pill>
                ) : null}
              </View>
              {t.open ? <Num p={p} value={money(total(t.lines))} size={16} /> : null}
              <LinkBtn
                p={p}
                label={t.open ? 'Ver cuenta' : 'Abrir mesa'}
                onPress={() => {
                  setSelected(t.id)
                  setArmedClose(false)
                  setArmedDelete(false)
                  setQueuedNotice(false)
                }}
              />
            </View>
          </Card>
        ))}
      </View>
      {s.tables.length === 0 ? (
        <Text style={[ui.sub, { color: p.mute }]}>Sin mesas registradas.</Text>
      ) : null}
    </View>
  )

  const detail = table ? (
    <View style={{ gap: 16 }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        {!split ? (
          <LinkBtn
            p={p}
            label="← VOLVER"
            disabled={tableBusy}
            onPress={() => {
              setSelected(null)
              setArmedClose(false)
              setArmedDelete(false)
              setQueuedNotice(false)
            }}
          />
        ) : null}
        <Text style={[ui.title, { color: p.ink, flex: 1 }]} numberOfLines={1}>
          {table.name}
        </Text>
        <StatusPill p={p} on={table.open} onLabel="Ocupada" offLabel="Libre" />
      </View>

      {!split ? statusPill : null}

      {!table.open ? (
        <Card p={p}>
          <Btn
            p={p}
            label="ABRIR MESA"
            loading={openingTableId === table.id}
            disabled={tableBusy}
            onPress={() => void handleOpenTable(table.id)}
          />
          <View style={{ height: 10 }} />
          {armedDelete ? (
            <View style={{ gap: 8 }}>
              <Btn
                p={p}
                label="CONFIRMAR ELIMINAR"
                variant="danger"
                loading={deletingTableId === table.id}
                disabled={tableBusy}
                onPress={() => void handleDeleteTable(table.id)}
              />
              <Btn
                p={p}
                label="CANCELAR"
                variant="ghost"
                disabled={tableBusy}
                onPress={() => setArmedDelete(false)}
              />
            </View>
          ) : (
            <Btn
              p={p}
              label="Eliminar mesa"
              variant="ghost"
              disabled={tableBusy}
              onPress={() => setArmedDelete(true)}
            />
          )}
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700', marginTop: 10 }]}>
              {s.hubError}
            </Text>
          ) : null}
        </Card>
      ) : null}

      {table.lines.length > 0 ? (
        <Card p={p}>
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
            <Text style={[ui.title, { color: p.ink, flex: 1 }]}>Cuenta abierta</Text>
            {!table.synced && s.pairing ? (
              <Pill p={p} tone="sun" dotColor={p.sunInk}>
                En cola local
              </Pill>
            ) : null}
          </View>
          <View style={{ marginTop: 10, gap: 8 }}>
            {table.lines.map((l) => (
              <View key={l.productId} style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <Text style={[ui.sub, { color: p.ink, flex: 1 }]} numberOfLines={2}>
                  {l.qty}× {s.products.find((x) => x.id === l.productId)?.name ?? 'Producto dado de baja'}
                </Text>
                <Num p={p} value={money(l.qty * price(l.productId))} size={13} />
              </View>
            ))}
          </View>
          <View style={{ marginTop: 12 }}>
            <Num p={p} value={money(total(table.lines))} size={30} />
          </View>
        </Card>
      ) : null}

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]}>Agregar productos</Text>
        <View style={{ marginTop: 10, gap: 8 }}>
          {s.products.map((prod) => {
            const available = s.productAvailable[prod.id] !== false
            const selectedProduct = available && pick.productId === prod.id
            return (
              <View
                key={prod.id}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 8,
                  borderWidth: 1,
                  borderColor: selectedProduct ? p.brand : p.line,
                  borderRadius: 12,
                  padding: 10,
                  opacity: available ? 1 : 0.5,
                }}
              >
                <View style={{ flex: 1 }}>
                  <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={2}>
                    {prod.name}
                  </Text>
                  <Num p={p} value={money(prod.price)} size={12} color={p.mute} />
                </View>
                {!available ? (
                  <Pill p={p} tone="mute">
                    Dada de baja
                  </Pill>
                ) : selectedProduct ? (
                  <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <Btn
                      p={p}
                      label="−"
                      variant="ghost"
                      disabled={tableBusy}
                      style={{ minWidth: 48 }}
                      onPress={() => setPick((c) => ({ ...c, qty: Math.max(1, c.qty - 1) }))}
                    />
                    <Num p={p} value={String(pick.qty)} size={16} />
                    <Btn
                      p={p}
                      label="+"
                      variant="ghost"
                      disabled={tableBusy}
                      style={{ minWidth: 48 }}
                      onPress={() => setPick((c) => ({ ...c, qty: c.qty + 1 }))}
                    />
                  </View>
                ) : (
                  <LinkBtn
                    p={p}
                    label="ELEGIR"
                    color={p.brand}
                    disabled={tableBusy}
                    onPress={() => setPick({ productId: prod.id, qty: 1 })}
                  />
                )}
              </View>
            )
          })}
        </View>
        <View style={{ marginTop: 12, gap: 8 }}>
          <Btn
            p={p}
            label="AGREGAR"
            loading={addingItem}
            disabled={!pick.productId || tableBusy}
            onPress={() => void handleAddItem()}
          />
          {table.open ? (
            armedClose ? (
              <View style={{ gap: 8 }}>
                <Btn
                  p={p}
                  label={`CONFIRMAR CIERRE · ${money(total(table.lines))}`}
                  variant="danger"
                  loading={closingTableId === table.id}
                  disabled={tableBusy}
                  onPress={() => void handleCloseTable()}
                />
                <Btn
                  p={p}
                  label="CANCELAR"
                  variant="ghost"
                  disabled={tableBusy}
                  onPress={() => setArmedClose(false)}
                />
              </View>
            ) : (
              <Btn
                p={p}
                label="CERRAR MESA"
                variant="danger"
                disabled={tableBusy}
                onPress={() => void handleCloseTable()}
              />
            )
          ) : null}
          {queuedNotice && s.connection !== 'online' ? (
            <Text style={[ui.sub, { color: p.sunInk, fontWeight: '700' }]}>
              Guardado en cola local ({s.pending} pendientes). Se enviará al reconectar.
            </Text>
          ) : null}
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
        </View>
      </Card>
    </View>
  ) : (
    <Card p={p}>
      <Text style={[ui.sub, { color: p.mute }]}>Elegí una mesa para ver su cuenta.</Text>
    </Card>
  )

  if (split) {
    return (
      <View style={{ flexDirection: 'row', gap: 16 }}>
        <View style={{ flex: 1 }}>{list}</View>
        <View style={{ flex: 1 }}>{detail}</View>
      </View>
    )
  }
  return selected && table ? detail : list
}
