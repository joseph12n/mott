import { useEffect, useState } from 'react'
import { Text, useWindowDimensions, View } from 'react-native'

import { useStore } from '../store'
import { BREAKPOINTS } from '../theme'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Pill, styles as ui } from '../ui'

export default function Proveedores() {
  const s = useStore()
  const p = usePalette()
  const { width } = useWindowDimensions()
  const [sel, setSel] = useState<string | null>(null)
  const [form, setForm] = useState({ name: '', phone: '', note: '' })
  const [editForm, setEditForm] = useState({ name: '', phone: '', note: '' })
  const [armedDelete, setArmedDelete] = useState(false)

  const supplier = s.suppliers.find((x) => x.id === sel) ?? null
  const split = width >= BREAKPOINTS.twoPane

  useEffect(() => {
    if (supplier) {
      setEditForm({
        name: supplier.name,
        phone: supplier.phone,
        note: supplier.note,
      })
      setArmedDelete(false)
    }
  }, [supplier?.id, supplier?.name, supplier?.phone, supplier?.note])

  const handleCreate = async () => {
    if (!form.name.trim()) return
    const ok = await s.createSupplier(form.name, form.phone, form.note)
    if (ok) {
      setForm({ name: '', phone: '', note: '' })
    }
  }

  const handleUpdate = async () => {
    if (!supplier || !editForm.name.trim()) return
    await s.updateSupplier(supplier.id, {
      name: editForm.name,
      phone: editForm.phone,
      note: editForm.note,
    })
  }

  const handleDelete = async () => {
    if (!supplier) return
    const ok = await s.deleteSupplier(supplier.id)
    setArmedDelete(false)
    if (ok) {
      setSel(null)
    }
  }

  const hasEditChanges =
    supplier !== null &&
    (editForm.name.trim() !== supplier.name ||
      editForm.phone.trim() !== supplier.phone ||
      editForm.note.trim() !== supplier.note)

  const list = (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Proveedores"
        sub={`${s.suppliers.length} proveedores registrados en la agenda`}
      />

      <Card p={p}>
        <Text style={[ui.label, { color: p.mute }]}>Agregar proveedor</Text>
        <View style={{ marginTop: 8, gap: 8 }}>
          <Field
            p={p}
            value={form.name}
            onChange={(v) => setForm({ ...form, name: v })}
            placeholder="Nombre del proveedor"
          />
          <Field
            p={p}
            value={form.phone}
            onChange={(v) => setForm({ ...form, phone: v })}
            placeholder="Teléfono"
          />
          <Field
            p={p}
            value={form.note}
            onChange={(v) => setForm({ ...form, note: v })}
            placeholder="Nota o rubro (Carnes, Bebidas, días de entrega…)"
          />
          <Btn
            p={p}
            label="AGREGAR PROVEEDOR"
            disabled={!form.name.trim()}
            onPress={() => void handleCreate()}
          />
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
        </View>
      </Card>

      <View style={{ gap: 10 }}>
        {s.suppliers.map((x) => (
          <Card
            key={x.id}
            p={p}
            style={{
              borderColor: x.id === sel ? p.brand : p.line,
              borderWidth: x.id === sel ? 2 : 1,
            }}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <View style={{ flex: 1, gap: 4 }}>
                <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                  {x.name}
                </Text>
                <Text style={[ui.note, { color: p.mute }]} numberOfLines={1}>
                  {x.phone || 'Sin teléfono'}
                </Text>
                {x.note ? (
                  <Text style={[ui.sub, { color: p.ink }]} numberOfLines={2}>
                    {x.note}
                  </Text>
                ) : null}
              </View>
              {x.note ? (
                <Pill p={p} tone="mute">
                  {x.note}
                </Pill>
              ) : null}
            </View>
            <View style={{ marginTop: 6 }}>
              <LinkBtn p={p} label="VER FICHA" color={p.brand} onPress={() => setSel(x.id)} />
            </View>
          </Card>
        ))}
        {s.suppliers.length === 0 ? (
          <Text style={[ui.sub, { color: p.mute }]}>Todavía no hay proveedores registrados.</Text>
        ) : null}
      </View>
    </View>
  )

  const detail = supplier ? (
    <View style={{ gap: 16 }}>
      {!split ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <LinkBtn
            p={p}
            label="← VOLVER"
            onPress={() => {
              setSel(null)
              setArmedDelete(false)
            }}
          />
          <Text style={[ui.title, { color: p.ink, flex: 1 }]} numberOfLines={1}>
            {supplier.name}
          </Text>
        </View>
      ) : null}

      <Card p={p}>
        <Text style={[ui.title, { color: p.ink }]} numberOfLines={1}>
          {supplier.name}
        </Text>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          {supplier.note ? (
            <Pill p={p} tone="mute">
              {supplier.note}
            </Pill>
          ) : null}
          <Text style={[ui.note, { color: p.mute }]}>{supplier.phone || 'Sin teléfono'}</Text>
        </View>

        <Text style={[ui.label, { color: p.mute, marginTop: 16 }]}>Editar datos</Text>
        <View style={{ marginTop: 8, gap: 8 }}>
          <Field
            p={p}
            value={editForm.name}
            onChange={(v) => setEditForm({ ...editForm, name: v })}
            placeholder="Nombre del proveedor"
          />
          <Field
            p={p}
            value={editForm.phone}
            onChange={(v) => setEditForm({ ...editForm, phone: v })}
            placeholder="Teléfono"
          />
          <Field
            p={p}
            value={editForm.note}
            onChange={(v) => setEditForm({ ...editForm, note: v })}
            placeholder="Nota"
          />
          <Btn
            p={p}
            label="GUARDAR CAMBIOS"
            disabled={!editForm.name.trim() || !hasEditChanges}
            onPress={() => void handleUpdate()}
          />
        </View>

        <View style={{ marginTop: 14, gap: 8 }}>
          {armedDelete ? (
            <>
              <Btn
                p={p}
                label="CONFIRMAR ELIMINAR"
                variant="danger"
                onPress={() => void handleDelete()}
              />
              <Btn
                p={p}
                label="CANCELAR"
                variant="ghost"
                onPress={() => setArmedDelete(false)}
              />
            </>
          ) : (
            <Btn
              p={p}
              label="ELIMINAR PROVEEDOR"
              variant="ghost"
              onPress={() => setArmedDelete(true)}
            />
          )}
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
        </View>
      </Card>
    </View>
  ) : (
    <Card p={p}>
      <Text style={[ui.sub, { color: p.mute }]}>Elegí un proveedor para ver o editar su ficha.</Text>
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
  return supplier && sel ? detail : list
}
