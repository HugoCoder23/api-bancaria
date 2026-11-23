import { Link } from 'react-router-dom';
import { ArrowLeftRight, Ban, CheckCircle2, Users, Wallet, XCircle } from 'lucide-react';
import { api } from '../api/client';
import type { Account, Currency, Page, Summary } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { useAsync } from '../lib/useAsync';
import { fmtMoney, fmtNumber } from '../lib/format';
import { AccountCard } from '../components/AccountCard';
import { Alert, Card, Empty, Loading, PageHeader, StatCard } from '../components/ui';

export function DashboardPage() {
  const { can } = useAuth();
  return can('CUSTOMER') ? <CustomerDashboard /> : <StaffDashboard />;
}

function CustomerDashboard() {
  const { user } = useAuth();
  const { data, error, loading } = useAsync(() => api.get<Page<Account>>('/api/accounts', { size: 50 }), []);
  const accounts = data?.content ?? [];
  const totals = accounts
    .filter((a) => a.status !== 'CLOSED')
    .reduce<Partial<Record<Currency, number>>>((acc, a) => ({ ...acc, [a.currency]: (acc[a.currency] ?? 0) + a.balance }), {});

  return (
    <>
      <PageHeader title={`Hola, ${user?.name.split(' ')[0] ?? ''}`} subtitle="Resumen de tus cuentas"
        actions={<Link className="btn btn-primary" to="/transferir"><ArrowLeftRight size={16} /> Transferir</Link>} />
      {error && <Alert>{error}</Alert>}
      {loading && !data ? <Loading /> : (
        <>
          <div className="stats">
            {(Object.keys(totals) as Currency[]).map((c) => (
              <StatCard key={c} icon={<Wallet size={20} />} tone="violet" label={`Saldo total en ${c}`} value={fmtMoney(totals[c], c)} />
            ))}
            <StatCard icon={<CheckCircle2 size={20} />} tone="green" label="Cuentas activas"
              value={fmtNumber(accounts.filter((a) => a.status === 'ACTIVE').length)} />
          </div>
          {accounts.length === 0 ? (
            <Card><Empty>Aún no tienes cuentas. <Link className="link" to="/cuentas">Abre la primera</Link>.</Empty></Card>
          ) : (
            <div className="account-grid">{accounts.map((a) => <AccountCard key={a.id} account={a} />)}</div>
          )}
        </>
      )}
    </>
  );
}

function StaffDashboard() {
  const { data, error, loading } = useAsync(() => api.get<Summary>('/api/reports/summary'), []);
  return (
    <>
      <PageHeader title="Panel de operación" subtitle="Indicadores generales del banco ficticio (hoy = UTC)"
        actions={<Link className="btn btn-primary" to="/clientes"><Users size={16} /> Clientes</Link>} />
      {error && <Alert>{error}</Alert>}
      {loading && !data ? <Loading /> : data && (
        <>
          <div className="stats">
            <StatCard icon={<Users size={20} />} tone="violet" label="Clientes" value={fmtNumber(data.customers)} />
            <StatCard icon={<Wallet size={20} />} tone="blue" label="Cuentas activas" value={fmtNumber(data.activeAccounts)}
              hint={`${fmtNumber(data.blockedAccounts)} bloqueadas`} />
            <StatCard icon={<CheckCircle2 size={20} />} tone="green" label="Operaciones hoy" value={fmtNumber(data.completedToday)} />
            <StatCard icon={<XCircle size={20} />} tone="amber" label="Rechazos hoy" value={fmtNumber(data.rejectedToday)} />
          </div>
          <div className="grid-dashboard">
            <Card title="Saldos administrados por moneda">
              {data.balances.length === 0 ? <Empty>Sin cuentas abiertas</Empty> : (
                <ul className="currency-list">
                  {data.balances.map((b) => (
                    <li key={b.currency}><span>{b.currency} · {fmtNumber(b.count)} cuentas</span><strong>{fmtMoney(b.amount, b.currency)}</strong></li>
                  ))}
                </ul>
              )}
            </Card>
            <Card title="Volumen operado hoy">
              {data.volumeToday.length === 0 ? <Empty><Ban size={16} /> Sin operaciones hoy</Empty> : (
                <ul className="currency-list">
                  {data.volumeToday.map((b) => (
                    <li key={b.currency}><span>{b.currency} · {fmtNumber(b.count)} mov.</span><strong>{fmtMoney(b.amount, b.currency)}</strong></li>
                  ))}
                </ul>
              )}
            </Card>
          </div>
        </>
      )}
    </>
  );
}
