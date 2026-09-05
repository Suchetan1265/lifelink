const TONE = {
  RAISED: 'tone-open',
  MATCHED: 'tone-progress',
  CONFIRMED: 'tone-progress',
  FULFILLED: 'tone-good',
  ESCALATED: 'tone-warn',
  EXPIRED: 'tone-bad',
  CANCELLED: 'tone-muted',
  NOTIFIED: 'tone-open',
  ACCEPTED: 'tone-good',
  DECLINED: 'tone-muted',
  CRITICAL: 'tone-bad',
  HIGH: 'tone-warn',
  NORMAL: 'tone-muted',
  PENDING: 'tone-warn',
  ACTIVE: 'tone-good',
  DISABLED: 'tone-bad',
};

export default function StatusChip({ value }) {
  if (!value) return null;
  return <span className={`chip ${TONE[value] ?? 'tone-muted'}`}>{value}</span>;
}
