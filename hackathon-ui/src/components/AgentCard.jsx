import { useState } from 'react';
import { StatusPill } from './StatusPill.jsx';

const NEXT_STATUS_LABEL = {
  AVAILABLE: 'Take offline',
  BUSY:      'Take offline',
  OFFLINE:   'Mark available',
};

const NEXT_STATUS = {
  AVAILABLE: 'OFFLINE',
  BUSY:      'OFFLINE',
  OFFLINE:   'AVAILABLE',
};

export function AgentCard({ agent, onStatusChange }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function toggle() {
    const target = NEXT_STATUS[agent.status];
    if (!target) return;
    setBusy(true);
    setError(null);
    try {
      await onStatusChange(agent.id, target);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="agent-row">
      <div className="agent-main">
        <div className="agent-name">{agent.name}</div>
        <div className="agent-id">{agent.id} · {agent.activeOrderCount} active</div>
      </div>
      <div className="agent-actions">
        <StatusPill status={agent.status} />
        <button className="btn btn-mini" onClick={toggle} disabled={busy}>
          {busy ? '…' : NEXT_STATUS_LABEL[agent.status]}
        </button>
      </div>
      {error && <div className="error-line">{error}</div>}
    </li>
  );
}
