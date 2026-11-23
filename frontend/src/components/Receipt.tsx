import { CheckCircle2, XCircle } from 'lucide-react';
import type { Transaction } from '../api/types';
import { fmtAccount, fmtDateTime, fmtMoney, TX_TYPE_LABEL } from '../lib/format';
import { TxStatusBadge } from './ui';

/** Comprobante de una operación (exitosa o rechazada). */
export function Receipt({ tx, replayed }: { tx: Transaction; replayed?: boolean }) {
  const ok = tx.status === 'COMPLETED';
  return (
    <div className="receipt">
      <div className="receipt-head">
        <span className={`icon ${ok ? 'tone-green' : 'tone-amber'}`}>{ok ? <CheckCircle2 size={22} /> : <XCircle size={22} />}</span>
        <div>
          <div className="strong">{TX_TYPE_LABEL[tx.type]} {ok ? 'aplicada' : 'rechazada'}</div>
          <div className="muted small">{fmtDateTime(tx.createdAt)}</div>
        </div>
        <span style={{ marginLeft: 'auto' }}><TxStatusBadge status={tx.status} /></span>
      </div>
      <div className={`receipt-amount ${ok ? '' : 'amount-rejected'}`}>{fmtMoney(tx.amount, tx.currency)}</div>
      {replayed && (
        <p className="hint-box" style={{ marginTop: 0 }}>
          Reintento detectado: se devolvió el resultado original y no se aplicó un segundo movimiento.
        </p>
      )}
      <dl className="kv">
        <dt>Referencia</dt><dd className="mono">{tx.reference}</dd>
        {tx.sourceAccountNumber && <><dt>Cuenta origen</dt><dd className="mono">{fmtAccount(tx.sourceAccountNumber)}</dd></>}
        {tx.destinationAccountNumber && <><dt>Cuenta destino</dt><dd className="mono">{fmtAccount(tx.destinationAccountNumber)}</dd></>}
        {tx.description && <><dt>Concepto</dt><dd>{tx.description}</dd></>}
        {tx.sourceBalanceAfter !== undefined && <><dt>Saldo origen después</dt><dd>{fmtMoney(tx.sourceBalanceAfter, tx.currency)}</dd></>}
        {tx.destinationBalanceAfter !== undefined && <><dt>Saldo destino después</dt><dd>{fmtMoney(tx.destinationBalanceAfter, tx.currency)}</dd></>}
        {tx.failureReason && <><dt>Motivo</dt><dd className="text-red">{tx.failureReason}</dd></>}
      </dl>
    </div>
  );
}
