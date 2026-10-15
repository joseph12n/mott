import { useState } from 'react'
import { Text, View } from 'react-native'

import { money, uid } from '../data'
import { useStore } from '../store'
import { usePalette } from '../usePalette'
import { Btn, Card, Field, Header, LinkBtn, Num, styles as ui } from '../ui'

// Gastos ported from the web master (views/Gastos.tsx): running total, add
// form and the list, with an inline two-tap delete instead of a dialog.
export default function Gastos() {
  const s = useStore()
  const p = usePalette()
  const [concept, setConcept] = useState('')
  const [amount, setAmount] = useState('')
  const [armed, setArmed] = useState<string | null>(null)

  const total = s.expenses.reduce((a, e) => a + e.amount, 0)

  return (
    <View style={{ gap: 16 }}>
      <Header
        p={p}
        title="Gastos"
        sub="Egresos registrados del servicio"
        right={
          <Card p={p}>
            <Text style={[ui.note, { color: p.mute }]}>Total del día</Text>
            <Num p={p} value={money(total)} size={24} />
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
            disabled={!concept.trim() || !Number(amount)}
            onPress={() => {
              s.setExpenses((list) => [
                { id: uid(), concept: concept.trim(), amount: Number(amount), date: 'Hoy' },
                ...list,
              ])
              setConcept('')
              setAmount('')
            }}
          />
        </View>
      </Card>

      <Card p={p}>
        <View style={{ gap: 12 }}>
          {s.expenses.map((e) => (
            <View key={e.id} style={{ gap: 6, borderBottomWidth: 1, borderBottomColor: p.line, paddingBottom: 10 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                <View style={{ flex: 1 }}>
                  <Text style={[ui.btnLabel, { color: p.ink }]} numberOfLines={1}>
                    {e.concept}
                  </Text>
                  <Text style={[ui.note, { color: p.mute }]}>{e.date}</Text>
                </View>
                <Num p={p} value={money(e.amount)} size={14} />
              </View>
              {armed === e.id ? (
                <View style={{ flexDirection: 'row', gap: 12 }}>
                  <LinkBtn
                    p={p}
                    label="CONFIRMAR"
                    color={p.berry}
                    onPress={() => {
                      s.setExpenses((list) => list.filter((x) => x.id !== e.id))
                      setArmed(null)
                    }}
                  />
                  <LinkBtn p={p} label="CANCELAR" color={p.mute} onPress={() => setArmed(null)} />
                </View>
              ) : (
                <LinkBtn p={p} label="Eliminar" color={p.mute} onPress={() => setArmed(e.id)} />
              )}
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
