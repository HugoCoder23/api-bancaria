import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError, newIdempotencyKey } from '../api/client';
import type { Account, Transaction } from '../api/types';
import { amountError, amountValue, fmtAccount, fmtMoney, ACCOUNT_TYPE_LABEL } from '../lib/format';
import { Alert, Field } from './ui';
import { Receipt } from './Receipt';

export type MoneyOp = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';

interface Props {
  op: MoneyOp;
  /** Cuentas propias activas (para elegir el origen en transferencias). */
  accounts: Account[];
  /** Cuenta fija (depósito/retiro, o origen preseleccionado en transferencias). */
  account?: Account;
  onDone?: (tx: Transaction) => void;
  onCancel?: () => void;
}

const SUBMIT_LABEL: Record<MoneyOp, string> = { DEPOSIT: 'Depositar', WITHDRAWAL: 'Retirar', TRANSFER: 'Transferir' };

/**
 * Formulario de operación de dinero. La Idempotency-Key se genera una vez por operación y se REUTILIZA si el
 * usuario reintenta tras un error de red o del servidor: así un doble clic o un reintento nunca duplican el
 * movimiento. Solo se genera una clave nueva cuando se cambian los datos o tras completar la operación.
 */
export function MoneyForm({ op, accounts, account, onDone, onCancel }: Props) {
  const [sourceId, setSourceId] = useState<number | ''>(account?.id ?? accounts[0]?.id ?? '');
  const [destination, setDestination] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [key, setKey] = useState(newIdempotencyKey);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [touched, setTouched] = useState(false);
  const [result, setResult] = useState<{ tx: Transaction; replayed: boolean } | null>(null);

  const source = useMemo(() => account ?? accounts.find((a) => a.id === sourceId), [account, accounts, sourceId]);
  const amountErr = touched ? amountError(amount) : null;
  const destErr = touched && op === 'TRANSFER' && destination.replace(/[\s-]/g, '').length < 12
    ? 'Indica los 12 dígitos de la cuenta destino' : null;

  // Cambiar los datos de la operación = operación distinta = clave nueva.
  const edit = <T,>(setter: (v: T) => void) => (v: T) => { setter(v); setKey(newIdempotencyKey()); setError(null); };

  async function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (amountError(amount) || !source || (op === 'TRANSFER' && destination.replace(/[\s-]/g, '').length < 12)) return;
    setBusy(true);
    setError(null);
    const body = { amount: amountValue(amount), description: description.trim() || undefined };
    try {
      const r = op === 'TRANSFER'
        ? await api.money<Transaction>('/api/transfers',
            { ...body, sourceAccountId: source.id, destinationAccountNumber: destination.replace(/[\s-]/g, '') }, key)
        : await api.money<Transaction>(`/api/accounts/${source.id}/${op === 'DEPOSIT' ? 'deposits' : 'withdrawals'}`, body, key);
      setResult({ tx: r.data, replayed: r.replayed });
      setKey(newIdempotencyKey());
      onDone?.(r.data);
    } catch (err) {
      const apiErr = err instanceof ApiError ? err : new ApiError({ status: 0, message: 'Error inesperado' });
      if (apiErr.status === 422 && apiErr.reference) {
        // Rechazo registrado: se muestra su comprobante.
        try {
          const tx = await api.get<Transaction>(`/api/transactions/${apiErr.reference}`);
          setResult({ tx, replayed: false });
          setKey(newIdempotencyKey());
          onDone?.(tx);
          return;
        } catch { /* se muestra el error tal cual */ }
      }
      setError(apiErr);
    } finally {
      setBusy(false);
    }
  }

  if (result) {
    return (
      <div className="form">
        <Receipt tx={result.tx} replayed={result.replayed} />
        <div className="form-actions">
          <Link className="btn btn-ghost" to={`/operaciones/${result.tx.reference}`}>Ver comprobante</Link>
          <button className="btn btn-primary" onClick={() => { setResult(null); setAmount(''); setDescription(''); setTouched(false); }}>
            Nueva operación
          </button>
          {onCancel && <button className="btn btn-ghost" onClick={onCancel}>Cerrar</button>}
        </div>
      </div>
    );
  }

  return (
    <form className="form" onSubmit={submit} noValidate>
      {error && (
        <Alert>
          {error.message}
          {(error.status === 0 || error.status >= 500) && ' · Puedes reintentar: la operación no se duplicará.'}
        </Alert>
      )}

      {op === 'TRANSFER' && !account ? (
        <Field label="Cuenta origen" required>
          <select value={sourceId} onChange={(e) => edit(setSourceId)(Number(e.target.value))}>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {ACCOUNT_TYPE_LABEL[a.type]} {fmtAccount(a.number)} · {fmtMoney(a.balance, a.currency)}
              </option>
            ))}
          </select>
        </Field>
      ) : source && (
        <div className="readonly-product">
          {ACCOUNT_TYPE_LABEL[source.type]} {fmtAccount(source.number)}
          <div className="muted small">Saldo disponible: {fmtMoney(source.balance, source.currency)}</div>
        </div>
      )}

      {op === 'TRANSFER' && (
        <Field label="Cuenta destino" required error={destErr ?? error?.fieldErrors.destinationAccountNumber}
          hint="Número de 12 dígitos. Puede ser tuya o de otro cliente (misma moneda).">
          <input className="mono" inputMode="numeric" placeholder="9900 0000 0000" value={destination}
            onChange={(e) => edit(setDestination)(e.target.value)} />
        </Field>
      )}

      <div className="grid-2">
        <Field label={`Importe${source ? ` (${source.currency})` : ''}`} required
          error={amountErr ?? error?.fieldErrors.amount}>
          <input inputMode="decimal" placeholder="0.00" value={amount} onChange={(e) => edit(setAmount)(e.target.value)} />
        </Field>
        <Field label="Concepto" error={error?.fieldErrors.description}>
          <input maxLength={140} placeholder="Opcional" value={description}
            onChange={(e) => edit(setDescription)(e.target.value)} />
        </Field>
      </div>

      <p className="hint-box" style={{ margin: 0 }}>
        Clave de idempotencia de esta operación: <span className="mono">{key.slice(0, 8)}…</span>
      </p>

      <div className="form-actions">
        {onCancel && <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancelar</button>}
        <button className="btn btn-primary" disabled={busy || !source}>{busy ? 'Procesando…' : SUBMIT_LABEL[op]}</button>
      </div>
    </form>
  );
}
