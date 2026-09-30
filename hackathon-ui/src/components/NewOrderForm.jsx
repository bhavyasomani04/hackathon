import { useState } from 'react';
import { api } from '../api.js';

function suggestOrderId() {
  // ORD-<random 3 digits> — will be checked server-side for duplicates.
  const n = Math.floor(100 + Math.random() * 900);
  return `ORD-${n}`;
}

export function NewOrderForm({ agents, onCreated, onClose }) {
  const assignable = agents.filter(a => a.status !== 'OFFLINE');

  const [id, setId] = useState(suggestOrderId);
  const [description, setDescription] = useState('');
  const [agentId, setAgentId] = useState(assignable[0]?.id || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function submit(e) {
    e.preventDefault();
    if (!id.trim() || !description.trim() || !agentId) {
      setError('All fields are required.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.createOrder({ id: id.trim(), description: description.trim(), agentId });
      await onCreated();
      onClose();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={e => e.stopPropagation()} onSubmit={submit}>
        <header className="modal-head">
          <h3>New order</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">×</button>
        </header>

        <div className="modal-body">
          <label className="field">
            <span>Order ID</span>
            <input
              type="text"
              value={id}
              onChange={e => setId(e.target.value)}
              placeholder="ORD-123"
              autoFocus
            />
          </label>

          <label className="field">
            <span>Description</span>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              placeholder="Groceries — HSR to BTM"
              rows={2}
            />
          </label>

          <label className="field">
            <span>Assign to agent</span>
            <select value={agentId} onChange={e => setAgentId(e.target.value)}>
              {assignable.length === 0 && <option value="">(no available agents)</option>}
              {assignable.map(a => (
                <option key={a.id} value={a.id}>
                  {a.id} — {a.name} ({a.status}, {a.activeOrderCount} active)
                </option>
              ))}
            </select>
          </label>

          {error && <div className="error-line">{error}</div>}
        </div>

        <footer className="modal-actions">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn-accept" disabled={busy || assignable.length === 0}>
            {busy ? 'Creating…' : 'Create order'}
          </button>
        </footer>
      </form>
    </div>
  );
}
