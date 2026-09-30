export type DatePreset = "7" | "30" | "90" | "all"

export function presetRange(preset: DatePreset): { from: string; to: string } {
  const to = new Date()
  const from = new Date()
  if (preset === "all") {
    from.setFullYear(2000, 0, 1)
  } else {
    from.setDate(to.getDate() - (Number(preset) - 1))
  }
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) }
}
