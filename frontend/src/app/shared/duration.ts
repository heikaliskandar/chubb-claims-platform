const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function durationSince(iso: string, now: number = Date.now()): string {
  const elapsed = Math.max(0, now - new Date(iso).getTime());
  if (elapsed < HOUR) {
    const minutes = Math.max(1, Math.round(elapsed / MINUTE));
    return minutes === 1 ? '1 minute' : `${minutes} minutes`;
  }
  if (elapsed < DAY) {
    const hours = Math.round(elapsed / HOUR);
    return hours === 1 ? '1 hour' : `${hours} hours`;
  }
  const days = Math.round(elapsed / DAY);
  return days === 1 ? '1 day' : `${days} days`;
}
