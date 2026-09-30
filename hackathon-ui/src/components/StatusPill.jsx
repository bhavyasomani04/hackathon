const CLASS_BY_STATUS = {
  AVAILABLE: 'pill pill-available',
  BUSY:      'pill pill-busy',
  OFFLINE:   'pill pill-offline',
};

export function StatusPill({ status }) {
  return <span className={CLASS_BY_STATUS[status] || 'pill'}>{status}</span>;
}
