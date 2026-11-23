import { useState } from 'react';
import { Search } from 'lucide-react';
import { api } from '../api/client';
import type { AuditEvent, AuditResult, Page } from '../api/types';
import { useAsync, useDebounced } from '../lib/useAsync';
import { fmtDateTime } from '../lib/format';
import { Alert, AuditResultBadge, Card, Empty, Loading, PageHeader, Pagination } from '../components/ui';

const ACTIONS = ['LOGIN', 'CUSTOMER_CREATE', 'CUSTOMER_STATUS', 'ACCOUNT_OPEN', 'ACCOUNT_STATUS', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER'];

export function AuditPage() {
  const [actor, setActor] = useState('');
  const [action, setAction] = useState('');
  const [result, setResult] = useState<AuditResult | ''>('');
  const [page, setPage] = useState(0);
  const a = useDebounced(actor);
  const { data, error, loading } = useAsync(
    () => api.get<Page<AuditEvent>>('/api/audit-events', { actor: a, action, result, page, size: 25 }),
    [a, action, result, page]);

  return (
    <>
      <PageHeader title="Auditoría" subtitle="Quién hizo qué y cuándo. Nunca incluye contraseñas, tokens ni números de cuenta completos." />
      <Card>
        <div className="filters">
          <label className="search"><Search size={16} />
            <input placeholder="Filtrar por usuario (email)" value={actor} onChange={(e) => { setActor(e.target.value); setPage(0); }} />
          </label>
          <select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
            <option value="">Todas las acciones</option>
            {ACTIONS.map((x) => <option key={x}>{x}</option>)}
          </select>
          <select value={result} onChange={(e) => { setResult(e.target.value as AuditResult | ''); setPage(0); }}>
            <option value="">Todos los resultados</option>
            <option value="SUCCESS">Éxito</option>
            <option value="REJECTED">Rechazado</option>
            <option value="DENIED">Denegado</option>
          </select>
        </div>
        {error && <Alert>{error}</Alert>}
        {loading && !data ? <Loading /> : data && data.content.length === 0 ? <Empty>Sin eventos</Empty> : data && (
          <>
            <div className={`table-wrap ${loading ? 'is-loading' : ''}`}>
              <table className="table">
                <thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Recurso</th><th>Resultado</th><th>Detalle</th></tr></thead>
                <tbody>
                  {data.content.map((e) => (
                    <tr key={e.id}>
                      <td className="nowrap">{fmtDateTime(e.createdAt)}</td>
                      <td>{e.actor}</td>
                      <td className="mono">{e.action}</td>
                      <td className="mono small">{e.resource ?? '—'}</td>
                      <td><AuditResultBadge result={e.result} /></td>
                      <td className="small">{e.detail ?? ''}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  );
}
