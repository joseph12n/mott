import { useRef, useState } from 'react'
import { Text, View } from 'react-native'

import { formatDay, isToday, money } from '../data'
import { useStore } from '../store'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, Num, styles as ui } from '../ui'

export default function Gastos() {
  const s = useStore()
  const p = usePalette()
  const [concept, setConcept] = useState('')
  const [amount, setAmount] = useState('')
  const [adding, setAdding] = useState(false)
  const addingRef = useRef(false)

  const todayTotal = s.expenses
    .filter((e) => isToday(e.date))
    .reduce((a, e) => a + e.amount, 0)

  const handleAdd = async () => {
    if (adding || addingRef.current) return
    const numAmount = Number(amount)
    if (!concept.trim() || !numAmount || numAmount <= 0) return
    addingRef.current = true
    setAdding(true)
    try {
      const ok = await s.addExpense(concept.trim(), numAmount)
      if (ok) {
        setConcept('')
        setAmount('')
      }
    } finally {
      addingRef.current = false
      setAdding(false)
    }
  }

  return (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Gastos"
        sub="Egresos registrados del servicio"
        right={
          <Card p={p}>
            <Text style={[ui.note, { color: p.mute }]}>Total del día</Text>
            <Num p={p} value={money(todayTotal)} size={24} />
          </Card>
        }
      />

      <Card p={p}>
        <View style={{ gap: 8 }}>
          <Field p={p} value={concept} onChange={setConcept} placeholder="Concepto" />
          <Field p={p} value={amount} onChange={setAmount} placeholder="Monto" keyboardType="decimal-pad" />
          <Btn
            p={p}
            label="REGISTRAR GASTO"
            loading={adding}
            disabled={!concept.trim() || !Number(amount) || Number(amount) <= 0 || adding}
            onPress={() => void handleAdd()}
          />
          <Text style={[ui.note, { color: p.mute }]}>
            {s.pairing
              ? 'Los gastos pertenecen al servidor de la PC y quedan registrados de forma permanente.'
              : 'Gastos guardados localmente en el teléfono. Vinculá la PC para sincronizarlos.'}
          </Text>
          {s.hubError ? (
            <Text style={[ui.sub, { color: p.berry, fontWeight: '700' }]}>{s.hubError}</Text>
          ) : null}
        </View>
      </Card>

      <Card p={p}>
        <View style={{ gap: 12 }}>
          {s.expenses.map((e) => (
            <View
              key={e.id}
              style={{
                gap: 6,
                borderBottomWidth: 1,
                borderBottomColor: p.line,
                paddingBottom: 10,
              }}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <View style={{ flex: 1 }}>
                  <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                    {e.concept}
                  </Text>
                  <Text style={[ui.note, { color: p.mute }]}>{formatDay(e.date) || e.date}</Text>
                </View>
                <Num p={p} value={money(e.amount)} size={14} />
              </View>
            </View>
          ))}
          {s.expenses.length === 0 ? (
            <Text style={[ui.sub, { color: p.mute }]}>Todavía no registraste gastos.</Text>
          ) : null}
        </View>
      </Card>
    </View>
  )
}
