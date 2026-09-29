const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080'

// Runs ZARA source against the backend's POST /api/run endpoint.
// Throws only for network/transport failures (backend unreachable) -- a ZARA
// program error (syntax error, division by zero, etc.) is a normal 200 response
// with a non-null `error` field, not an exception, since that's the interpreter
// doing its job correctly, not the request failing.
export async function runZara(code) {
  let response
  try {
    response = await fetch(`${API_BASE}/api/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    })
  } catch {
    throw new Error(`Could not reach the ZARA server at ${API_BASE}`)
  }

  let body
  try {
    body = await response.json()
  } catch {
    throw new Error(`Server returned an unexpected response (status ${response.status})`)
  }

  if (!response.ok && body.error === undefined) {
    throw new Error(`Server error (status ${response.status})`)
  }

  return { output: body.output ?? '', error: body.error ?? null }
}
