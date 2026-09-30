// Single place for the backend base URL. Change here if the Spring port moves.
const BASE = 'http://localhost:8080';

async function req(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  if (!res.ok) {
    let body = null;
    try { body = await res.json(); } catch { /* ignore */ }
    const message = body?.message || `HTTP ${res.status}`;
    const err = new Error(message);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  listAgents:            ()             => req('/agents'),
  updateAgentStatus:     (id, status)   => req(`/agents/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  listPendingSuggestions: ()            => req('/suggestions?status=PENDING'),
  resolveSuggestion:     (id, status)   => req(`/suggestions/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  suggestForOrder:       (orderId)      => req(`/orders/${orderId}/suggest`, { method: 'POST' }),
  listOrders:            (status)       => req(status ? `/orders?status=${status}` : '/orders'),
  createOrder:           (payload)      => req('/orders', { method: 'POST', body: JSON.stringify(payload) }),
};
