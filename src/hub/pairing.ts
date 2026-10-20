// Pairing code parser and URL normalization for mitt hub connections.

export type Pairing = {
  baseUrl: string
  token: string
}

export function normalizeBaseUrl(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, '')
  if (!trimmed) {
    throw new Error('Base URL is required')
  }

  let withScheme = trimmed
  const schemeMatch = /^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/(.*)$/.exec(trimmed)
  if (schemeMatch) {
    const scheme = schemeMatch[1].toLowerCase()
    if (scheme !== 'http' && scheme !== 'https') {
      throw new Error('Base URL must use http or https')
    }
    withScheme = `${scheme}://${schemeMatch[2]}`
  } else if (trimmed.includes('://')) {
    throw new Error('Invalid base URL scheme')
  } else {
    withScheme = `http://${trimmed}`
  }

  let parsed: URL
  try {
    parsed = new URL(withScheme)
  } catch {
    throw new Error('Invalid base URL')
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('Base URL must use http or https')
  }
  if (!parsed.hostname) {
    throw new Error('Base URL is missing host')
  }

  const authority = withScheme.slice(parsed.protocol.length + 2).split('/')[0]
  const colonIdx = authority.lastIndexOf(':')
  const bracketIdx = authority.lastIndexOf(']')
  if (colonIdx === -1 || colonIdx < bracketIdx) {
    throw new Error('Base URL is missing explicit port')
  }
  const portStr = authority.slice(colonIdx + 1)
  if (!/^\d+$/.test(portStr)) {
    throw new Error('Base URL has invalid port')
  }
  const portNum = Number(portStr)
  if (portNum < 1 || portNum > 65535) {
    throw new Error('Base URL port out of range')
  }
  if (parsed.pathname && parsed.pathname !== '/') {
    throw new Error('Base URL must not include a path')
  }

  return `${parsed.protocol}//${parsed.hostname}:${portNum}`
}

export function parsePairingCode(raw: string): Pairing {
  const input = raw.trim()
  if (!input) {
    throw new Error('Pairing code is empty')
  }

  let rawUrl = ''
  let rawToken = ''

  if (/^mitt:\/\//i.test(input)) {
    const match = /^mitt:\/\/pair\?(.+)$/i.exec(input)
    if (!match) {
      throw new Error('Pairing URI must be mitt://pair?url=...&token=...')
    }
    const params = new URLSearchParams(match[1])
    rawUrl = (params.get('url') ?? '').trim()
    rawToken = (params.get('token') ?? '').trim()
    if (!rawUrl) {
      throw new Error('Pairing code is missing url')
    }
    if (!rawToken) {
      throw new Error('Pairing code is missing token')
    }
    if (!/^https?:\/\//i.test(rawUrl)) {
      throw new Error('Pairing URL must start with http:// or https://')
    }
  } else {
    const sep = input.indexOf('|')
    if (sep <= 0 || sep >= input.length - 1 || input.indexOf('|', sep + 1) !== -1) {
      throw new Error('Pairing code must be mitt://pair?... or url|token')
    }
    rawUrl = input.slice(0, sep).trim()
    rawToken = input.slice(sep + 1).trim()
  }

  const baseUrl = normalizeBaseUrl(rawUrl)
  if (rawToken.length < 16) {
    throw new Error('Pairing token must be at least 16 characters')
  }

  return { baseUrl, token: rawToken }
}

export function pairingUrl(pairing: Pairing): string {
  const baseUrl = normalizeBaseUrl(pairing.baseUrl)
  const token = pairing.token.trim()
  if (token.length < 16) {
    throw new Error('Pairing token must be at least 16 characters')
  }
  return `mitt://pair?url=${encodeURIComponent(baseUrl)}&token=${encodeURIComponent(token)}`
}
