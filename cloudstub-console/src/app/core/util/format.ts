/** Format an ISO-8601 duration (e.g. "PT1H30M5.2S") as a compact human string. */
export function formatDuration(iso: string): string {
  const match = iso.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?/);
  if (!match) return iso;
  const h = parseInt(match[1] ?? '0');
  const m = parseInt(match[2] ?? '0');
  const s = Math.floor(parseFloat(match[3] ?? '0'));
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}
