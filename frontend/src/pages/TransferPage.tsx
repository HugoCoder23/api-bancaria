import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { Account, Page } from '../api/types';
import { useAsync } from '../lib/useAsync';
import { fmtAccount } from '../lib/format';
import { MoneyForm } from '../components/MoneyForm';
import { Alert, Card, Empty, Loading, PageHeader } from '../components/ui';

export function TransferPage() {
  const { data, error, loading, reload } = useAsync(
    () => api.get<Page<Account>>('/api/accounts', { status: 'ACTIVE', size: 50 }), []);
  const accounts = data?.content ?? [];

  return (
    <>
      <PageHeader title="Transferir" subtitle="Entre tus cuentas o a la cuenta de otro cliente, en la misma moneda" />
      <div className="grid-dashboard">
        <Card className="form-card">
          {error && <Alert>{error}</Alert>}
          {loading && !data ? <Loading /> : accounts.length === 0 ? (
            <Empty>No tienes cuentas activas. <Link className="link" to="/cuentas">Abre una cuenta</Link>.</Empty>
          ) : (
            <MoneyForm op="TRANSFER" accounts={accounts} onDone={reload} />
          )}
        </Card>
        <Card title="Tus cuentas">
          {accounts.length === 0 ? <Empty>—</Empty> : (
            <ul className="currency-list">
              {accounts.map((a) => (
                <li key={a.id}>
                  <span>{a.type === 'SAVINGS' ? 'Ahorro' : 'Corriente'} {a.currency}</span>
                  <button className="btn btn-ghost btn-sm mono" title="Copiar número"
                    onClick={() => navigator.clipboard?.writeText(a.number)}>{fmtAccount(a.number)}</button>
                </li>
              ))}
            </ul>
          )}
          <p className="hint-box" style={{ marginBottom: 0 }}>
            Cuentas demo de otros clientes: pide a Luis su número entrando como él, o usa la lista del operador.
          </p>
        </Card>
      </div>
    </>
  );
}
