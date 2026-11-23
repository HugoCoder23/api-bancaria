import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, Plus, Search, Unlock, Wallet } from 'lucide-react';
import { api, ApiError } from '../api/client';
import type { Customer, CustomerStatus, Page } from '../api/types';
import { useAsync, useDebounced } from '../lib/useAsync';
import { fmtDate } from '../lib/format';
import { Alert, Card, CustomerStatusBadge, Empty, Field, Loading, Modal, PageHeader, Pagination } from '../components/ui';

const SIZE = 20;

export function CustomersPage() {
  const navigate = useNavigate();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<CustomerStatus | ''>('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [msg, setMsg] = useState<{ tone: 'error' | 'success'; text: string } | null>(null);
  const query = useDebounced(q);

  const { data, error, loading, reload } = useAsync(
    () => api.get<Page<Customer>>('/api/customers', { q: query, status, page, size: SIZE }), [query, status, page]);

  async function toggle(c: Customer) {
    const next: CustomerStatus = c.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE';
    try {
      await api.patch(`/api/customers/${c.id}/status`, { status: next });
      setMsg({ tone: 'success', text: `${c.fullName}: ${next === 'BLOCKED' ? 'bloqueado' : 'desbloqueado'}` });
      reload();
    } catch (err) {
      setMsg({ tone: 'error', text: err instanceof ApiError ? err.message : 'No se pudo cambiar el estado' });
    }
  }

  return (
    <>
      <PageHeader title="Clientes" subtitle="Clientes ficticios del banco de demostración"
        actions={<button className="btn btn-primary" onClick={() => setCreating(true)}><Plus size={16} /> Nuevo cliente</button>} />
      {msg && <Alert tone={msg.tone}>{msg.text}</Alert>}
      <Card>
        <div className="filters">
          <label className="search"><Search size={16} />
            <input placeholder="Buscar por nombre o email" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          </label>
          <select value={status} onChange={(e) => { setStatus(e.target.value as CustomerStatus | ''); setPage(0); }}>
            <option value="">Todos</option>
            <option value="ACTIVE">Activos</option>
            <option value="BLOCKED">Bloqueados</option>
          </select>
        </div>
        {error && <Alert>{error}</Alert>}
        {loading && !data ? <Loading /> : data && data.content.length === 0 ? <Empty>No hay clientes</Empty> : data && (
          <>
            <div className={`table-wrap ${loading ? 'is-loading' : ''}`}>
              <table className="table">
                <thead><tr><th>Cliente</th><th>Teléfono</th><th>Estado</th><th className="num">Cuentas</th><th>Alta</th><th /></tr></thead>
                <tbody>
                  {data.content.map((c) => (
                    <tr key={c.id}>
                      <td><div className="strong">{c.fullName}</div><div className="muted small">{c.email}</div></td>
                      <td className="nowrap">{c.phone}</td>
                      <td><CustomerStatusBadge status={c.status} /></td>
                      <td className="num">{c.accountCount}</td>
                      <td className="nowrap">{fmtDate(c.createdAt)}</td>
                      <td className="actions-col">
                        <button className="btn btn-icon" title="Ver cuentas" onClick={() => navigate(`/cuentas?cliente=${c.id}`)}><Wallet size={16} /></button>
                        <button className="btn btn-icon" title={c.status === 'ACTIVE' ? 'Bloquear' : 'Desbloquear'} onClick={() => toggle(c)}>
                          {c.status === 'ACTIVE' ? <Lock size={16} /> : <Unlock size={16} />}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
      {creating && <NewCustomerModal onClose={() => setCreating(false)}
        onCreated={(c) => { setCreating(false); setMsg({ tone: 'success', text: `Cliente creado: ${c.fullName}. Ya puede iniciar sesión.` }); reload(); }} />}
    </>
  );
}

function NewCustomerModal({ onClose, onCreated }: { onClose: () => void; onCreated: (c: Customer) => void }) {
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.post<Customer>('/api/customers', form));
    } catch (err) {
      setError(err instanceof ApiError ? err : new ApiError({ status: 0, message: 'Error inesperado' }));
      setBusy(false);
    }
  }

  const fe = error?.fieldErrors ?? {};
  return (
    <Modal title="Nuevo cliente (ficticio)" onClose={onClose}>
      <form className="form" onSubmit={submit} noValidate>
        {error && <Alert>{error.message}</Alert>}
        <Field label="Nombre completo" required error={fe.fullName}><input value={form.fullName} onChange={set('fullName')} /></Field>
        <div className="grid-2">
          <Field label="Email" required error={fe.email}><input type="email" value={form.email} onChange={set('email')} /></Field>
          <Field label="Teléfono ficticio" required error={fe.phone}><input value={form.phone} onChange={set('phone')} placeholder="+52 55 0000 0000" /></Field>
        </div>
        <Field label="Contraseña de acceso" required error={fe.password} hint="Mínimo 8 caracteres. El cliente la usará para iniciar sesión.">
          <input type="password" autoComplete="new-password" value={form.password} onChange={set('password')} />
        </Field>
        <p className="hint-box" style={{ margin: 0 }}>Usa solo datos inventados. No captures datos personales reales.</p>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Guardando…' : 'Crear cliente'}</button>
        </div>
      </form>
    </Modal>
  );
}
