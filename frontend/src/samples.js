// Served as static files from public/samples/ (copied from the repo's top-level
// sample/ directory -- see CLAUDE.md). Fetched on demand rather than bundled so
// picking a sample doesn't require importing raw text at build time.
export const SAMPLES = [
  { label: 'Arithmetic', file: 'program1.zara' },
  { label: 'Strings', file: 'program2.zara' },
  { label: 'Conditional', file: 'program3.zara' },
  { label: 'Loop', file: 'program4.zara' },
]

export async function loadSample(file) {
  const response = await fetch(`/samples/${file}`)
  if (!response.ok) {
    throw new Error(`Could not load sample ${file}`)
  }
  return response.text()
}
