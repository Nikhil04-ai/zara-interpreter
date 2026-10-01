import { useCallback, useEffect, useRef, useState } from 'react'
import CodeMirror from '@uiw/react-codemirror'
import { runZara, API_BASE } from './api'
import { SAMPLES, loadSample } from './samples'
import { zaraLanguage } from './zaraLanguage'
import { editorExtensions } from './editorTheme'
import './App.css'

// Blank by default -- a new visitor gets an empty editor (or whatever a share
// link restores), not a pre-filled example. The placeholder below is just
// grayed-out editor hint text, not real content, so it never gets submitted.
const DEFAULT_CODE = ''

const MAX_UPLOAD_BYTES = 1_000_000 // 1MB -- generous for a teaching-language program, cheap to guard

function codeFromShareLink() {
  const match = window.location.hash.match(/code=([^&]+)/)
  if (!match) return null
  try {
    return decodeURIComponent(escape(atob(decodeURIComponent(match[1]))))
  } catch {
    return null
  }
}

export default function App() {
  const [code, setCode] = useState(() => codeFromShareLink() ?? DEFAULT_CODE)
  const [result, setResult] = useState(null)
  const [networkError, setNetworkError] = useState(null)
  const [running, setRunning] = useState(false)
  const [activeSample, setActiveSample] = useState(null)
  const [theme, setTheme] = useState(() => localStorage.getItem('zara-theme') || 'dark')
  const [expanded, setExpanded] = useState(false)
  const [toast, setToast] = useState(null)
  const [modal, setModal] = useState(null) // 'docs' | 'about' | null
  const [settingsOpen, setSettingsOpen] = useState(false)
  const toastTimer = useRef(null)
  const fileInputRef = useRef(null)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('zara-theme', theme)
  }, [theme])

  // codeFromShareLink() in useState above only runs once, on mount -- it
  // won't catch the hash changing on an already-open tab (e.g. a same-document
  // fragment navigation, or a user editing the URL bar directly). This keeps
  // a share link working in that case too, not just on a fresh page load.
  useEffect(() => {
    function onHashChange() {
      const restored = codeFromShareLink()
      if (restored !== null) {
        setCode(restored)
        setResult(null)
        setNetworkError(null)
        setActiveSample(null)
      }
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  function showToast(message) {
    setToast(message)
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast(null), 2000)
  }

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

  async function onSelectSample(file) {
    setActiveSample(file)
    try {
      const text = await loadSample(file)
      setCode(text)
      setResult(null)
      setNetworkError(null)
    } catch (err) {
      setNetworkError(err.message)
    }
  }

  function onClear() {
    setCode('')
    setResult(null)
    setNetworkError(null)
    setActiveSample(null)
  }

  function onUploadClick() {
    fileInputRef.current?.click()
  }

  function onFileSelected(e) {
    const file = e.target.files?.[0]
    e.target.value = '' // reset so selecting the same file again still fires onChange

    if (!file) return

    if (!file.name.toLowerCase().endsWith('.zara')) {
      showToast('Please select a .zara file')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      showToast('File is too large (max 1MB)')
      return
    }

    const reader = new FileReader()
    reader.onload = () => {
      setCode(String(reader.result))
      setResult(null)
      setNetworkError(null)
      setActiveSample(null)
      showToast(`Loaded ${file.name}`)
    }
    reader.onerror = () => showToast(`Could not read ${file.name}`)
    reader.readAsText(file)
  }

  // navigator.clipboard.writeText can reject (denied permission, insecure
  // context, browser restrictions) -- falls back to the older execCommand
  // approach, and only gives up (with an honest toast) if both fail, rather
  // than leaving the click looking like it silently did nothing.
  async function copyToClipboard(text) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      try {
        const textarea = document.createElement('textarea')
        textarea.value = text
        textarea.style.position = 'fixed'
        textarea.style.opacity = '0'
        document.body.appendChild(textarea)
        textarea.select()
        const ok = document.execCommand('copy')
        document.body.removeChild(textarea)
        return ok
      } catch {
        return false
      }
    }
  }

  async function onCopyCode() {
    const ok = await copyToClipboard(code)
    showToast(ok ? 'Code copied to clipboard' : 'Could not copy — copy the code manually')
  }

  async function onShare() {
    const encoded = encodeURIComponent(btoa(unescape(encodeURIComponent(code))))
    const url = `${window.location.origin}${window.location.pathname}#code=${encoded}`
    const ok = await copyToClipboard(url)
    showToast(ok ? 'Shareable link copied' : 'Could not copy the link — check clipboard permissions')
  }

  return (
    <div className="shell">
      <header className="navbar">
        <div className="brand">
          <span className="brand-mark">Z</span>
          <span className="brand-name">Zara Interpreter</span>
        </div>

        <nav className="navlinks">
          <button className="navlink active">Run</button>
          <button className="navlink" onClick={() => setModal('docs')}>Documentation</button>
          <button className="navlink" onClick={() => setModal('about')}>About</button>
        </nav>

        <div className="navactions">
          <button
            className="theme-toggle"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            aria-label="Toggle light/dark theme"
            title="Toggle theme"
          >
            {theme === 'dark' ? '🌙' : '☀️'}
          </button>
          <div className="settings-wrap">
            <button
              className="icon-button"
              onClick={() => setSettingsOpen((s) => !s)}
              aria-label="Settings"
              title="Settings"
            >
              ⚙️
            </button>
            {settingsOpen && (
              <div className="settings-popover">
                <div className="settings-row">
                  <span className="settings-label">Backend URL</span>
                  <code className="settings-value">{API_BASE}</code>
                </div>
                <div className="settings-row">
                  <span className="settings-label">Theme</span>
                  <span className="settings-value">{theme}</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </header>

      <div className={`body ${expanded ? 'editor-expanded' : ''}`}>
        {!expanded && (
          <aside className="sidebar">
            <button className="upload-button" onClick={onUploadClick}>
              <span className="upload-button-icon">⬆</span>
              Upload a .zara file
            </button>

            <div className="sidebar-section-title">
              <span>Examples</span>
            </div>
            <ul className="example-list">
              {SAMPLES.map((s) => (
                <li key={s.file}>
                  <button
                    className={`example-item ${activeSample === s.file ? 'active' : ''}`}
                    onClick={() => onSelectSample(s.file)}
                  >
                    {s.label}
                  </button>
                </li>
              ))}
            </ul>

            <button className="promo-card" onClick={() => setModal('about')}>
              <div className="promo-title">Zara</div>
              <p className="promo-body">
                A mini scripting language interpreter, built from scratch for a course project.
              </p>
              <span className="promo-arrow">&rarr;</span>
            </button>
          </aside>
        )}

        <main className="editor-pane">
          <div className="tabbar">
            <div className="tab active">
              <span className="tab-icon">📄</span>
              main.zara
            </div>
            <button className="tab-add" disabled title="Multiple files aren't supported yet">
              +
            </button>
          </div>

          <div className="editor-toolbar">
            <input
              ref={fileInputRef}
              type="file"
              accept=".zara,text/plain"
              onChange={onFileSelected}
              className="visually-hidden"
            />
            <button className="icon-button small" onClick={onUploadClick} title="Upload a .zara file">
              ⬆
            </button>
            <button className="icon-button small" onClick={onCopyCode} title="Copy code">
              ⧉
            </button>
            <button
              className="icon-button small"
              onClick={() => setExpanded((v) => !v)}
              title={expanded ? 'Collapse' : 'Expand'}
            >
              {expanded ? '⤡' : '⤢'}
            </button>
          </div>

          <CodeMirror
            value={code}
            height="100%"
            theme="none"
            placeholder="Write your ZARA code here, or load an example from the sidebar…"
            extensions={[zaraLanguage, ...editorExtensions]}
            onChange={(value) => {
              setCode(value)
              setActiveSample(null)
            }}
            basicSetup={{ tabSize: 4 }}
            className="editor"
          />
        </main>

        {!expanded && (
          <aside className="right-panel">
            <button className="run-button" onClick={run} disabled={running}>
              <span className="run-icon">{running ? '⏳' : '▶'}</span>
              {running ? 'Running…' : 'Run Code'}
              <span className="run-hint">Ctrl + Enter</span>
            </button>

            <div className="action-row">
              <button className="ghost-button" onClick={onClear}>Clear</button>
              <button className="ghost-button" onClick={onShare}>Share</button>
            </div>

            <div className="panel-card output-card">
              <div className="panel-card-header">Output</div>
              <div className="panel-card-body">
                {networkError ? (
                  <span className="muted-warning">{networkError}</span>
                ) : running ? (
                  <span className="muted">Running…</span>
                ) : result && result.output ? (
                  <pre className="output-text">{result.output}</pre>
                ) : (
                  <span className="muted">Run your program to see output here.</span>
                )}
              </div>
            </div>

            <div className="panel-card error-card">
              <div className="panel-card-header">
                Error Console
                {result?.error && (
                  <button className="ghost-button tiny" onClick={() => setResult({ ...result, error: null })}>
                    Clear
                  </button>
                )}
              </div>
              <div className="panel-card-body">
                {result && result.error ? (
                  <pre className="error-text">{result.error}</pre>
                ) : (
                  <span className="muted success">No errors. 🎉</span>
                )}
              </div>
            </div>
          </aside>
        )}
      </div>

      {toast && <div className="toast">{toast}</div>}

      {modal && (
        <div className="modal-overlay" onClick={() => setModal(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <button className="modal-close" onClick={() => setModal(null)}>&times;</button>
            {modal === 'docs' ? <Documentation /> : <About />}
          </div>
        </div>
      )}
    </div>
  )
}

function Documentation() {
  return (
    <div className="doc-content">
      <h2>ZARA Language Reference</h2>
      <p className="muted">A small scripting language: variables, printing, conditionals, loops.</p>

      <h3>Variables</h3>
      <pre className="doc-code">{`set x = 10\nshow x`}</pre>

      <h3>Conditionals</h3>
      <pre className="doc-code">{`when score > 50:\n    show "Passed"\notherwise when score > 30:\n    show "Retry"\notherwise:\n    show "Failed"`}</pre>

      <h3>Loops</h3>
      <pre className="doc-code">{`loop 4:\n    show "hello"\n\nset i = 0\nwhile i < 5:\n    show i\n    set i = i + 1`}</pre>

      <h3>Operators</h3>
      <p>
        Arithmetic: <code>+ - * /</code> &middot; Comparison: <code>&gt; &lt; == != &gt;= &lt;=</code> &middot;
        Boolean: <code>and or not</code> &middot; Grouping: <code>( )</code>
      </p>

      <h3>Comments</h3>
      <pre className="doc-code">{`# this is a comment`}</pre>
    </div>
  )
}

function About() {
  return (
    <div className="doc-content">
      <h2>About ZARA</h2>
      <p>
        ZARA <em>(Zero-ceremony Arithmetic and Reasoning Assembler)</em> is a toy scripting language
        interpreter built from scratch in pure Java, with no external libraries or frameworks.
      </p>
      <p>
        The interpreter is a classic three-stage pipeline &mdash; Tokenizer &rarr; Parser &rarr; Evaluator
        &mdash; wired up behind a Spring Boot API, with this playground as its frontend.
      </p>
    </div>
  )
}
