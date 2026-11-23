import { useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Plus, Search } from 'lucide-react';
import { api, ApiError } from '../api/client';
import type { Account, AccountStatus, AccountType, Currency, Page } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync, useDebounced } from '../lib/useAsync';
import { ACCOUNT_TYPE_LABEL, fmtAccount, fmtDate, fmtMoney } from '../lib/format';
import { AccountCard } from '../components/AccountCard';
import { AccountStatusBadge, Alert, Card, Empty, Field, Loading, Modal, PageHeader, Pagination } from '../components/ui';

const SIZE = 20;

export function AccountsPage() {
  const { can } = useAuth();
  return can('CUSTOMER') ? <MyAccounts /> : <AllAccounts />;
}

function MyAccounts() {
  const [opening, setOpening] = useState(false);
  const navigate = useNavigate();
  const { data, error, loading } = useAsync(() => api.get<Page<Account>>('/api/accounts', { size: 50 }), []);
  return (
    <>
      <PageHeader title="Mis cuentas" subtitle="Toca una cuenta para ver sus movimientos y operar"
        actions={<button className="btn btn-primary" onClick={() => setOpening(true)}><Plus size={16} /> Abrir cuenta</button>} />
      {error && <Alert>{error}</Alert>}
      {loading && !data ? <Loading /> : (
        <div className="account-grid">
          {data?.content.map((a) => <AccountCard key={a.id} account={a} />)}
          <button className="account-card-new" onClick={() => setOpening(true)}><Plus size={22} /> Abrir cuenta</button>
        </div>
      )}
      {opening && <OpenAccountModal onClose={() => setOpening(false)} onCreated={(a) => navigate(`/cuentas/${a.id}`)} />}
    </>
  );
}

function AllAccounts() {
  const [params, setParams] = useSearchParams();
  const customerId = params.get('cliente') ?? '';
  const [number, setNumber] = useState('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [currency, setCurrency] = useState<Currency | ''>('');
  const [page, setPage] = useState(0);
  const [opening, setOpening] = useState(false);
  const navigate = useNavigate();
  const q = useDebounced(number);

  const { data, error, loading } = useAsync(
    () => api.get<Page<Account>>('/api/accounts', { customerId, number: q, status, currency, page, size: SIZE, sort: 'openedAt,desc' }),
    [customerId, q, status, currency, page]);

  return (
    <>
      <PageHeader title="Cuentas" subtitle={customerId ? 'Filtradas por cliente' : 'Todas las cuentas del banco ficticio'}
        actions={customerId && <button className="btn btn-primary" onClick={() => setOpening(true)}><Plus size={16} /> Abrir cuenta</button>} />
      <Card>
        <div className="filters">
          <label className="search"><Search size={16} />
            <input placeholder="Buscar por número de cuenta" value={number} onChange={(e) => { setNumber(e.target.value); setPage(0); }} />
          </label>
          <select value={status} onChange={(e) => { setStatus(e.target.value as AccountStatus | ''); setPage(0); }}>
            <option value="">Todos los estados</option>
            <option value="ACTIVE">Activas</option>
            <option value="BLOCKED">Bloqueadas</option>
            <option value="CLOSED">Cerradas</option>
          </select>
          <select value={currency} onChange={(e) => { setCurrency(e.target.value as Currency | ''); setPage(0); }}>
            <option value="">Todas las monedas</option>
            <option>MXN</option><option>USD</option><option>EUR</option>
          </select>
          {customerId && <button className="btn btn-ghost btn-sm" onClick={() => setParams({})}>Quitar filtro de cliente</button>}
        </div>
        {error && <Alert>{error}</Alert>}
        {loading && !data ? <Loading /> : data && data.content.length === 0 ? <Empty>No hay cuentas con esos filtros</Empty> : data && (
          <>
            <div className={`table-wrap ${loading ? 'is-loading' : ''}`}>
              <table className="table table-hover">
                <thead><tr><th>Número</th><th>Titular</th><th>Tipo</th><th>Estado</th><th>Apertura</th><th className="num">Saldo</th></tr></thead>
                <tbody>
                  {data.content.map((a) => (
                    <tr key={a.id} onClick={() => navigate(`/cuentas/${a.id}`)}>
                      <td className="mono nowrap">{fmtAccount(a.number)}</td>
                      <td>{a.customerName}</td>
                      <td>{ACCOUNT_TYPE_LABEL[a.type]} · {a.currency}</td>
                      <td><AccountStatusBadge status={a.status} /></td>
                      <td className="nowrap">{fmtDate(a.openedAt)}</td>
                      <td className="num strong nowrap">{fmtMoney(a.balance, a.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
      {opening && <OpenAccountModal customerId={Number(customerId)} onClose={() => setOpening(false)}
        onCreated={(a) => navigate(`/cuentas/${a.id}`)} />}
    </>
  );
}

function OpenAccountModal({ customerId, onClose, onCreated }:
  { customerId?: number; onClose: () => void; onCreated: (a: Account) => void }) {
  const [type, setType] = useState<AccountType>('CHECKING');
  const [currency, setCurrency] = useState<Currency>('MXN');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.post<Account>('/api/accounts', { type, currency, customerId }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No se pudo abrir la cuenta');
      setBusy(false);
    }
  }

  return (
    <Modal title="Abrir cuenta" onClose={onClose} width={440}>
      <form className="form" onSubmit={submit}>
        {error && <Alert>{error}</Alert>}
        <div className="grid-2">
          <Field label="Tipo">
            <select value={type} onChange={(e) => setType(e.target.value as AccountType)}>
              <option value="CHECKING">{ACCOUNT_TYPE_LABEL.CHECKING}</option>
              <option value="SAVINGS">{ACCOUNT_TYPE_LABEL.SAVINGS}</option>
            </select>
          </Field>
          <Field label="Moneda">
            <select value={currency} onChange={(e) => setCurrency(e.target.value as Currency)}>
              <option>MXN</option><option>USD</option><option>EUR</option>
            </select>
          </Field>
        </div>
        <p className="hint-box" style={{ margin: 0 }}>El número se genera automáticamente y la cuenta inicia con saldo 0.00.</p>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Abriendo…' : 'Abrir cuenta'}</button>
        </div>
      </form>
    </Modal>
  );
}
