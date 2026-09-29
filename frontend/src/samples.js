// Served as static files from public/samples/ (copied from the repo's top-level
// sample/ directory -- see CLAUDE.md). Fetched on demand rather than bundled so
// picking a sample doesn't require importing raw text at build time.
export const SAMPLES = [
  { label: '1. Arithmetic', file: 'program1.zara' },
  { label: '2. Strings', file: 'program2.zara' },
  { label: '3. Conditional', file: 'program3.zara' },
  { label: '4. Loop', file: 'program4.zara' },
  { label: '5. While Loop', file: 'program5.zara' },
  { label: '6. Else-if Chain', file: 'program6.zara' },
]

export async function loadSample(file) {
  const response = await fetch(`/samples/${file}`)
  if (!response.ok) {
    throw new Error(`Could not load sample ${file}`)
  }
  return response.text()
}
