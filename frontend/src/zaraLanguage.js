import { StreamLanguage } from '@codemirror/language'

// A lightweight token-stream definition for ZARA -- enough for coloring
// (keywords, strings, numbers, comments, operators), not a real grammar.
// Mirrors backend/src/main/java/com/zara/interpreter/Tokenizer.java's keyword
// list; keep the two in sync if ZARA syntax changes.
const KEYWORDS = new Set([
  'set', 'show', 'when', 'otherwise', 'loop', 'while', 'and', 'or', 'not',
])

const zaraTokenizer = {
  startState() {
    return {}
  },
  token(stream) {
    if (stream.eatSpace()) return null

    if (stream.match('#')) {
      stream.skipToEnd()
      return 'comment'
    }

    if (stream.match('"')) {
      while (!stream.eol()) {
        if (stream.next() === '"') break
      }
      return 'string'
    }

    if (stream.match(/^\d+(\.\d+)?/)) {
      return 'number'
    }

    if (stream.match(/^(!=|>=|<=|==)/)) {
      return 'operator'
    }

    if (stream.match(/^[+\-*/><()]/)) {
      return 'operator'
    }

    if (stream.match(/^[a-zA-Z_][a-zA-Z0-9_]*/)) {
      const word = stream.current()
      if (KEYWORDS.has(word)) return 'keyword'
      return 'variableName'
    }

    stream.next()
    return null
  },
}

export const zaraLanguage = StreamLanguage.define(zaraTokenizer)
