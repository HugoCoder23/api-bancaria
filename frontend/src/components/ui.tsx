import { useEffect, type ReactNode } from 'react';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import type { AccountStatus, AuditResult, CustomerStatus, TransactionStatus } from '../api/types';
import { ACCOUNT_STATUS_LABEL, fmtNumber } from '../lib/format';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function Card({ title, actions, children, className = '' }:
  { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card-header">
          {title && <h2>{title}</h2>}
          {actions}
        </header>
      )}
      {children}
    </section>
  );
}

export function StatCard({ icon, label, value, tone = 'blue', hint }:
  { icon: ReactNode; label: string; value: string; tone?: 'blue' | 'green' | 'amber' | 'violet'; hint?: string }) {
  return (
    <div className="stat">
      <div className={`stat-icon tone-${tone}`}>{icon}</div>
      <div style={{ minWidth: 0 }}>
        <div className="stat-value">{value}</div>
        <div className="stat-label">{label}</div>
        {hint && <div className="stat-hint">{hint}</div>}
      </div>
    </div>
  );
}

export function AccountStatusBadge({ status }: { status: AccountStatus }) {
  const tone = status === 'ACTIVE' ? 'green' : status === 'BLOCKED' ? 'amber' : 'gray';
  return <span className={`badge badge-${tone}`}>{ACCOUNT_STATUS_LABEL[status]}</span>;
}

export function CustomerStatusBadge({ status }: { status: CustomerStatus }) {
  return status === 'ACTIVE'
    ? <span className="badge badge-green">Activo</span>
    : <span className="badge badge-amber">Bloqueado</span>;
}

export function TxStatusBadge({ status }: { status: TransactionStatus }) {
  return status === 'COMPLETED'
    ? <span className="badge badge-green">Completada</span>
    : <span className="badge badge-red">Rechazada</span>;
}

export function AuditResultBadge({ result }: { result: AuditResult }) {
  const tone = result === 'SUCCESS' ? 'green' : result === 'REJECTED' ? 'amber' : 'red';
  const label = result === 'SUCCESS' ? 'Éxito' : result === 'REJECTED' ? 'Rechazado' : 'Denegado';
  return <span className={`badge badge-${tone}`}>{label}</span>;
}

export function Pagination({ page, totalPages, totalElements, size, onPage }:
  { page: number; totalPages: number; totalElements: number; size: number; onPage: (p: number) => void }) {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalElements, (page + 1) * size);
  return (
    <div className="pagination">
      <span className="muted">{fmtNumber(from)}–{fmtNumber(to)} de {fmtNumber(totalElements)}</span>
      <div className="pagination-buttons">
        <button className="btn btn-ghost btn-icon" disabled={page === 0} onClick={() => onPage(page - 1)}
          aria-label="Página anterior"><ChevronLeft size={16} /></button>
        <span className="page-indicator">{page + 1} / {Math.max(totalPages, 1)}</span>
        <button className="btn btn-ghost btn-icon" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}
          aria-label="Página siguiente"><ChevronRight size={16} /></button>
      </div>
    </div>
  );
}

export function Modal({ title, onClose, children, width = 520 }:
  { title: string; onClose: () => void; children: ReactNode; width?: number }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" style={{ maxWidth: width }} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="btn btn-ghost btn-icon" onClick={onClose} aria-label="Cerrar"><X size={18} /></button>
        </header>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, error, hint, children, required }:
  { label: string; error?: string; hint?: ReactNode; children: ReactNode; required?: boolean }) {
  return (
    <label className={`field ${error ? 'has-error' : ''}`}>
      <span className="field-label">{label}{required && <span className="req"> *</span>}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info'; children: ReactNode }) {
  return <div className={`alert alert-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Loading() {
  return <div className="loading"><span className="spinner" /> Cargando…</div>;
}

export function DemoBanner() {
  return <div className="demo-banner">Proyecto de portafolio · Todos los datos y el dinero son ficticios</div>;
}
