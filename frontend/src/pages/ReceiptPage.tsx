import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Search } from 'lucide-react';
import { api } from '../api/client';
import type { Transaction } from '../api/types';
import { useAsync } from '../lib/useAsync';
import { Receipt } from '../components/Receipt';
import { Alert, Card, Empty, Loading, PageHeader } from '../components/ui';

export function ReceiptPage() {
  const { reference } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(reference ?? '');

  function submit(e: FormEvent) {
    e.preventDefault();
    if (input.trim()) navigate(`/operaciones/${input.trim().toUpperCase()}`);
  }

  return (
    <>
      <PageHeader title="Comprobantes" subtitle="Consulta el resultado de una operación por su referencia" />
      <Card className="form-card">
        <form className="lookup" onSubmit={submit}>
          <input className="mono" placeholder="TRX-20261123-XXXXXXXX" value={input} onChange={(e) => setInput(e.target.value)} />
          <button className="btn btn-primary"><Search size={16} /> Consultar</button>
        </form>
      </Card>
      {reference ? <ReceiptLoader key={reference} reference={reference} /> : (
        <Card className="form-card"><Empty>Introduce una referencia o abre un movimiento desde el historial de una cuenta.</Empty></Card>
      )}
    </>
  );
}

function ReceiptLoader({ reference }: { reference: string }) {
  const { data, error, loading } = useAsync(() => api.get<Transaction>(`/api/transactions/${reference}`), [reference]);
  return (
    <Card className="form-card">
      {error && <Alert>{error}</Alert>}
      {loading && !data ? <Loading /> : data && <Receipt tx={data} />}
    </Card>
  );
}
