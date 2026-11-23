import type { AccountStatus, AccountType, Currency, Role, TransactionType } from '../api/types';

const moneyFormatters = new Map<string, Intl.NumberFormat>();
const number = new Intl.NumberFormat('es-MX');
const dateTime = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });

export const fmtMoney = (v: number | null | undefined, currency: Currency = 'MXN') => {
  let f = moneyFormatters.get(currency);
  if (!f) {
    f = new Intl.NumberFormat('es-MX', { style: 'currency', currency, currencyDisplay: 'code' });
    moneyFormatters.set(currency, f);
  }
  return f.format(v ?? 0);
};
export const fmtNumber = (v: number | null | undefined) => number.format(v ?? 0);
export const fmtDateTime = (iso: string) => dateTime.format(new Date(iso));
export const fmtDate = (iso: string) => dateOnly.format(new Date(iso));

/** "990000001234" → "9900 0000 1234" */
export const fmtAccount = (n?: string) => (n && /^\d{12}$/.test(n) ? n.replace(/(\d{4})(?=\d)/g, '$1 ') : n ?? '—');

export const ROLE_LABEL: Record<Role, string> = {
  CUSTOMER: 'Cliente',
  OPERATOR: 'Operador',
  ADMIN: 'Administrador',
};

export const ACCOUNT_TYPE_LABEL: Record<AccountType, string> = {
  SAVINGS: 'Ahorro',
  CHECKING: 'Corriente',
};

export const ACCOUNT_STATUS_LABEL: Record<AccountStatus, string> = {
  ACTIVE: 'Activa',
  BLOCKED: 'Bloqueada',
  CLOSED: 'Cerrada',
};

export const TX_TYPE_LABEL: Record<TransactionType, string> = {
  DEPOSIT: 'Depósito',
  WITHDRAWAL: 'Retiro',
  TRANSFER: 'Transferencia',
};

/** Valida un importe tecleado: positivo, máx. 2 decimales. Devuelve el mensaje de error o null. */
export function amountError(raw: string): string | null {
  const v = raw.trim().replace(',', '.');
  if (!v) return 'Indica un importe';
  if (!/^\d+(\.\d{1,2})?$/.test(v)) return 'Usa un número positivo con máximo 2 decimales';
  if (Number(v) <= 0) return 'El importe debe ser mayor que cero';
  if (Number(v) > 1_000_000) return 'El máximo por operación es 1,000,000.00';
  return null;
}

/** Se envía como texto para no perder precisión decimal (el backend lo lee como BigDecimal). */
export const amountValue = (raw: string) => raw.trim().replace(',', '.');
