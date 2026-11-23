import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowDownLeft, ArrowLeft, ArrowLeftRight, ArrowUpRight, Lock, Unlock, XCircle } from 'lucide-react';
import { api, ApiError } from '../api/client';
import type { Account, AccountStatus, HistoryLine, Page, TransactionStatus, TransactionType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../lib/useAsync';
import { ACCOUNT_TYPE_LABEL, fmtAccount, fmtDate, fmtDateTime, fmtMoney, TX_TYPE_LABEL } from '../lib/format';
import { MoneyForm, type MoneyOp } from '../components/MoneyForm';
import { AccountStatusBadge, Alert, Card, Empty, Loading, Modal, PageHeader, Pagination, TxStatusBadge } from '../components/ui';

const SIZE = 15;
const OP_TITLE: Record<MoneyOp, string> = { DEPOSIT: 'Depositar', WITHDRAWAL: 'Retirar', TRANSFER: 'Transferir' };

export function AccountDetailPage() {
  const { id } = useParams();
  const { can } = useAuth();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [type, setType] = useState<TransactionType | ''>('');
  const [status, setStatus] = useState<TransactionStatus | ''>('');
  const [op, setOp] = useState<MoneyOp | null>(null);
  const [statusMsg, setStatusMsg] = useState<{ tone: 'error' | 'success'; text: string } | null>(null);

  const account = useAsync(() => api.get<Account>(`/api/accounts/${id}`), [id]);
  const history = useAsync(
    () => api.get<Page<HistoryLine>>(`/api/accounts/${id}/transactions`, { type, status, page, size: SIZE }),
    [id, type, status, page]);

  const a = account.data;
  const isOwner = can('CUSTOMER');
  const isStaff = can('OPERATOR', 'ADMIN');
  const operable = a?.status === 'ACTIVE';

  function refresh() {
    account.reload();
    history.reload();
  }

  async function changeStatus(next: AccountStatus) {
    const reason = next === 'ACTIVE' ? undefined
      : window.prompt(next === 'CLOSED' ? 'Motivo del cierre (definitivo):' : 'Motivo del bloqueo:') ?? null;
    if (reason === null) return;
    try {
      account.setData(await api.patch<Account>(`/api/accounts/${id}/status`, { status: next, reason: reason || undefined }));
      setStatusMsg({ tone: 'success', text: 'Estado actualizado' });
    } catch (err) {
      setStatusMsg({ tone: 'error', text: err instanceof ApiError ? err.message : 'No se pudo cambiar el estado' });
    }
  }

  if (account.error) {
    return (
      <>
        <PageHeader title="Cuenta" />
        <Alert>{account.error}</Alert>
        <Link className="link" to="/cuentas"><ArrowLeft size={14} /> Volver a cuentas</Link>
      </>
    );
  }
  if (!a) return <Loading />;

  return (
    <>
      <PageHeader
        title={`${ACCOUNT_TYPE_LABEL[a.type]} ${a.currency}`}
        subtitle={<><span className="mono">{fmtAccount(a.number)}</span> · {a.customerName}</>}
        actions={<button className="btn btn-ghost" onClick={() => navigate(-1)}><ArrowLeft size={16} /> Volver</button>} />

      <Card>
        <div className="balance-hero">
          <div>
            <div className="muted small">Saldo disponible</div>
            <div className="big-number">{fmtMoney(a.balance, a.currency)}</div>
            <div className="muted small" style={{ marginTop: 6 }}>
              <AccountStatusBadge status={a.status} /> · Abierta el {fmtDate(a.openedAt)}
            </div>
          </div>
          {isOwner && (
            <div className="page-actions">
              <button className="btn btn-soft" disabled={!operable} onClick={() => setOp('DEPOSIT')}><ArrowDownLeft size={16} /> Depositar</button>
              <button className="btn btn-soft" disabled={!operable} onClick={() => setOp('WITHDRAWAL')}><ArrowUpRight size={16} /> Retirar</button>
              <button className="btn btn-primary" disabled={!operable} onClick={() => setOp('TRANSFER')}><ArrowLeftRight size={16} /> Transferir</button>
            </div>
          )}
          {isStaff && a.status !== 'CLOSED' && (
            <div className="page-actions">
              {a.status === 'ACTIVE'
                ? <button className="btn btn-ghost" onClick={() => changeStatus('BLOCKED')}><Lock size={16} /> Bloquear</button>
                : <button className="btn btn-ghost" onClick={() => changeStatus('ACTIVE')}><Unlock size={16} /> Desbloquear</button>}
              <button className="btn btn-ghost text-red" onClick={() => changeStatus('CLOSED')}><XCircle size={16} /> Cerrar cuenta</button>
            </div>
          )}
        </div>
        {!operable && isOwner && (
          <p className="hint-box" style={{ marginBottom: 0 }}>Esta cuenta no está activa: no admite depósitos, retiros ni transferencias.</p>
        )}
        {isStaff && <p className="hint-box" style={{ marginBottom: 0 }}>Como operador puedes consultar y administrar el estado, pero no mover fondos del cliente.</p>}
        {statusMsg && <div style={{ marginTop: 12 }}><Alert tone={statusMsg.tone}>{statusMsg.text}</Alert></div>}
      </Card>

      <Card title="Movimientos" actions={
        <div className="filters" style={{ margin: 0 }}>
          <select value={type} onChange={(e) => { setType(e.target.value as TransactionType | ''); setPage(0); }}>
            <option value="">Todos los tipos</option>
            <option value="DEPOSIT">Depósitos</option>
            <option value="WITHDRAWAL">Retiros</option>
            <option value="TRANSFER">Transferencias</option>
          </select>
          <select value={status} onChange={(e) => { setStatus(e.target.value as TransactionStatus | ''); setPage(0); }}>
            <option value="">Todos los estados</option>
            <option value="COMPLETED">Completados</option>
            <option value="REJECTED">Rechazados</option>
          </select>
        </div>
      }>
        {history.error && <Alert>{history.error}</Alert>}
        {history.loading && !history.data ? <Loading /> : history.data && history.data.content.length === 0 ? (
          <Empty>Sin movimientos todavía</Empty>
        ) : history.data && (
          <>
            <div className={`table-wrap ${history.loading ? 'is-loading' : ''}`}>
              <table className="table table-hover">
                <thead><tr><th>Fecha</th><th>Concepto</th><th>Contraparte</th><th>Estado</th><th className="num">Importe</th><th className="num">Saldo</th></tr></thead>
                <tbody>
                  {history.data.content.map((t) => (
                    <tr key={t.reference} onClick={() => navigate(`/operaciones/${t.reference}`)}>
                      <td className="nowrap">{fmtDateTime(t.createdAt)}</td>
                      <td>
                        <div className="strong">{TX_TYPE_LABEL[t.type]}</div>
                        <div className="muted small">{t.status === 'REJECTED' ? t.failureReason : t.description ?? t.reference}</div>
                      </td>
                      <td className="mono nowrap">{t.counterpartyAccountNumber ? fmtAccount(t.counterpartyAccountNumber) : '—'}</td>
                      <td><TxStatusBadge status={t.status} /></td>
                      <td className={`num nowrap ${t.status === 'REJECTED' ? 'amount-rejected' : t.direction === 'CREDIT' ? 'amount-in' : 'amount-out'}`}>
                        {t.direction === 'CREDIT' ? '+' : '−'}{fmtMoney(t.amount, a.currency)}
                      </td>
                      <td className="num nowrap">{t.balanceAfter !== undefined ? fmtMoney(t.balanceAfter, a.currency) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={history.data.page} size={history.data.size} totalPages={history.data.totalPages}
              totalElements={history.data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>

      {op && (
        <Modal title={OP_TITLE[op]} onClose={() => setOp(null)}>
          <MoneyForm op={op} account={a} accounts={[a]} onDone={refresh} onCancel={() => setOp(null)} />
        </Modal>
      )}
    </>
  );
}
