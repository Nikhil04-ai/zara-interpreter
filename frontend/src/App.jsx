import { useCallback, useEffect, useState } from 'react'
import CodeMirror from '@uiw/react-codemirror'
import { runZara } from './api'
import { SAMPLES, loadSample } from './samples'
import './App.css'

const DEFAULT_CODE = `set score = 85
when score > 50:
    show "Passed"
otherwise:
    show "Failed"
`

export default function App() {
  const [code, setCode] = useState(DEFAULT_CODE)
  const [result, setResult] = useState(null) // { output, error } from the interpreter
  const [networkError, setNetworkError] = useState(null) // transport-level failure
  const [running, setRunning] = useState(false)
  const [sampleFile, setSampleFile] = useState('')

  const run = useCallback(async () => {
    setRunning(true)
    setNetworkError(null)
    try {
      const r = await runZara(code)
      setResult(r)
    } catch (err) {
      setNetworkError(err.message)
      setResult(null)
    } finally {
      setRunning(false)
    }
  }, [code])

  useEffect(() => {
    function onKeyDown(e) {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault()
        run()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [run])

  async function onSelectSample(e) {
    const file = e.target.value
    setSampleFile(file)
    if (!file) return
    try {
      const text = await loadSample(file)
      setCode(text)
      setResult(null)
      setNetworkError(null)
    } catch (err) {
      setNetworkError(err.message)
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>ZARA Playground</h1>
        <p className="tagline">Write, run, and see output from the ZARA scripting language.</p>
      </header>

      <div className="toolbar">
        <select value={sampleFile} onChange={onSelectSample} aria-label="Load a sample program">
          <option value="">Load a sample&hellip;</option>
          {SAMPLES.map((s) => (
            <option key={s.file} value={s.file}>
              {s.label}
            </option>
          ))}
        </select>
        <button className="run-button" onClick={run} disabled={running}>
          {running ? 'Running…' : 'Run (Ctrl+Enter)'}
        </button>
      </div>

      <main className="panes">
        <section className="pane editor-pane">
          <h2>Code</h2>
          <CodeMirror
            value={code}
            height="100%"
            onChange={(value) => setCode(value)}
            basicSetup={{ tabSize: 4 }}
            className="editor"
          />
        </section>

        <section className="pane output-pane">
          <h2>Output</h2>
          <div className="output-body">
            {networkError && <div className="banner banner-network">{networkError}</div>}

            {!networkError && result === null && !running && (
              <p className="placeholder">Run your program to see output here.</p>
            )}

            {running && <p className="placeholder">Running…</p>}

            {!running && result && (
              <>
                <pre className="output-text">
                  {result.output ? result.output : <span className="placeholder">(no output)</span>}
                </pre>
                {result.error && <div className="banner banner-error">{result.error}</div>}
              </>
            )}
          </div>
        </section>
      </main>
    </div>
  )
}
