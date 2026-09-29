import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'
import { EditorView } from '@codemirror/view'

// Maps the plain string tags zaraLanguage.js returns ('keyword', 'string', ...)
// to actual highlight colors. @uiw/react-codemirror's `<CodeMirror>` maps a
// StreamLanguage token's returned tag name to the matching @lezer/highlight
// tag automatically via `tokenTable`-like convention, so this HighlightStyle
// keys off the standard tag set.
export const zaraHighlightStyle = HighlightStyle.define([
  { tag: t.keyword, color: '#c792ea', fontWeight: '600' },
  { tag: t.string, color: '#ecc48d' },
  { tag: t.number, color: '#f78c6c' },
  { tag: t.comment, color: '#6b7394', fontStyle: 'italic' },
  { tag: t.variableName, color: '#82aaff' },
  { tag: t.operator, color: '#89ddff' },
])

export const zaraEditorTheme = EditorView.theme({
  '&': {
    backgroundColor: 'transparent',
    height: '100%',
  },
  '.cm-scroller': {
    fontFamily: 'var(--mono)',
    fontSize: '14px',
    lineHeight: '1.6',
  },
  '.cm-gutters': {
    backgroundColor: 'transparent',
    border: 'none',
    color: 'var(--text-faint)',
  },
  '.cm-activeLine': {
    backgroundColor: 'var(--active-line)',
  },
  '.cm-activeLineGutter': {
    backgroundColor: 'transparent',
    color: 'var(--text-h)',
  },
  '&.cm-focused': {
    outline: 'none',
  },
}, { dark: true })

export const editorExtensions = [zaraEditorTheme, syntaxHighlighting(zaraHighlightStyle)]
