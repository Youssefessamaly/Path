/** Formats an ISO-8601 timestamp/date (as the backend's OffsetDateTime/LocalDate fields
 *  serialize to) for display, falling back to the raw string if parsing fails. */
export function formatDisplayDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
