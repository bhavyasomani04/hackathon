import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api.js';
import { SuggestionCard } from './components/SuggestionCard.jsx';
import { AgentCard } from './components/AgentCard.jsx';
import { NewOrderForm } from './components/NewOrderForm.jsx';
import './App.css';

const POLL_INTERVAL_MS = 3000;

function useNow() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);
  return now;
}

function relativeTime(date, now) {
  if (!date) return 'never';
  const s = Math.floor((now.getTime() - date.getTime()) / 1000);
  if (s < 2)  return 'just now';
  if (s < 60) return `${s}s ago`;
  return `${Math.floor(s / 60)}m ago`;
}

export default function App() {
  const [suggestions, setSuggestions] = useState([]);
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [globalError, setGlobalError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(null);
  const [polling, setPolling] = useState(true);
  const [showNewOrder, setShowNewOrder] = useState(false);
  const inFlight = useRef(false);
  const now = useNow();

  const refresh = useCallback(async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    try {
      const [s, a] = await Promise.all([
        api.listPendingSuggestions(),
        api.listAgents(),
      ]);
      setSuggestions(s);
      setAgents(a);
      setLastUpdated(new Date());
      setGlobalError(null);
    } catch (e) {
      setGlobalError(e.message || 'Failed to load');
    } finally {
      inFlight.current = false;
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  useEffect(() => {
    if (!polling) return undefined;
    const t = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(t);
  }, [polling, refresh]);

  const handleResolveSuggestion = useCallback(async (id, status) => {
    await api.resolveSuggestion(id, status);
    await refresh();
  }, [refresh]);

  const handleAgentStatus = useCallback(async (id, status) => {
    await api.updateAgentStatus(id, status);
    // T-4 re-plan is async and fires on OFFLINE transitions — give it a beat before refreshing.
    setTimeout(refresh, 400);
  }, [refresh]);

  const agenticCount = suggestions.filter(s => s.triggerReason === 'AGENT_OFFLINE').length;

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>ZipRun Ops</h1>
          <p className="subtitle">AI-assisted reassignment console</p>
        </div>
        <div className="header-controls">
          <div className="status-line">
            <span className="dot" data-live={polling}></span>
            Updated {relativeTime(lastUpdated, now)}
          </div>
          <label className="poll-toggle">
            <input
              type="checkbox"
              checked={polling}
              onChange={e => setPolling(e.target.checked)}
            />
            Auto-refresh
          </label>
          <button className="btn btn-mini" onClick={refresh} disabled={loading}>
            Refresh now
          </button>
          <button className="btn btn-accept btn-mini" onClick={() => setShowNewOrder(true)}>
            + New order
          </button>
        </div>
      </header>

      {showNewOrder && (
        <NewOrderForm
          agents={agents}
          onCreated={refresh}
          onClose={() => setShowNewOrder(false)}
        />
      )}

      {globalError && (
        <div className="global-error">
          <strong>Can't reach backend:</strong> {globalError}. Is Spring Boot running on :8080?
        </div>
      )}

      <div className="layout">
        <main className="queue">
          <div className="section-header">
            <h2>Reassignment queue</h2>
            <div className="section-meta">
              {suggestions.length} pending
              {agenticCount > 0 && <span className="agentic-count"> · {agenticCount} agentic</span>}
            </div>
          </div>

          {loading && suggestions.length === 0 ? (
            <div className="empty-state">Loading suggestions…</div>
          ) : suggestions.length === 0 ? (
            <div className="empty-state">
              <div className="empty-title">No pending suggestions</div>
              <div className="empty-hint">Take an agent offline to fire the agentic loop.</div>
            </div>
          ) : (
            <div className="queue-list">
              {suggestions.map(s => (
                <SuggestionCard
                  key={s.id}
                  suggestion={s}
                  onResolved={handleResolveSuggestion}
                />
              ))}
            </div>
          )}
        </main>

        <aside className="roster">
          <div className="section-header">
            <h2>Agents</h2>
            <div className="section-meta">{agents.length} total</div>
          </div>

          {agents.length === 0 && !loading ? (
            <div className="empty-state small">No agents seeded.</div>
          ) : (
            <ul className="agent-list">
              {agents.map(a => (
                <AgentCard key={a.id} agent={a} onStatusChange={handleAgentStatus} />
              ))}
            </ul>
          )}
        </aside>
      </div>

      <footer className="app-footer">
        <span>Polling every {POLL_INTERVAL_MS / 1000}s.</span>
        <span>Backend: <code>localhost:8080</code></span>
      </footer>
    </div>
  );
}
