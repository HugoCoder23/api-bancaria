import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  Landmark, LayoutDashboard, Wallet, ArrowLeftRight, Receipt, Users, ShieldCheck, LogOut, Menu, X,
} from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABEL } from '../lib/format';
import { DemoBanner } from './ui';

export function Layout() {
  const { user, logout, can } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const [lastPath, setLastPath] = useState(location.pathname);
  if (lastPath !== location.pathname) { setLastPath(location.pathname); setOpen(false); }

  const isCustomer = can('CUSTOMER');
  const links = [
    { to: '/', label: 'Inicio', icon: LayoutDashboard, end: true },
    { to: '/cuentas', label: isCustomer ? 'Mis cuentas' : 'Cuentas', icon: Wallet },
    ...(isCustomer ? [{ to: '/transferir', label: 'Transferir', icon: ArrowLeftRight }] : []),
    { to: '/operaciones', label: 'Comprobantes', icon: Receipt },
    ...(can('OPERATOR', 'ADMIN') ? [{ to: '/clientes', label: 'Clientes', icon: Users }] : []),
    ...(can('ADMIN') ? [{ to: '/auditoria', label: 'Auditoría', icon: ShieldCheck }] : []),
  ];

  return (
    <div className="shell">
      <header className="topbar">
        <button className="btn btn-ghost btn-icon" onClick={() => setOpen(!open)} aria-label="Menú">
          {open ? <X size={20} /> : <Menu size={20} />}
        </button>
        <span className="brand-inline"><Landmark size={18} /> Banca Demo</span>
      </header>
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand"><span className="brand-logo"><Landmark size={20} /></span> Banca Demo</div>
        <nav>
          {links.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              <Icon size={18} /> {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          {user && (
            <div className="user-chip">
              <div className="avatar">{user.name.charAt(0)}</div>
              <div className="user-meta">
                <div className="user-name">{user.name}</div>
                <div className="user-role">{ROLE_LABEL[user.role]}</div>
              </div>
            </div>
          )}
          <button className="nav-link logout" onClick={logout}><LogOut size={18} /> Cerrar sesión</button>
        </div>
      </aside>
      {open && <div className="sidebar-scrim" onClick={() => setOpen(false)} />}
      <main className="content">
        <DemoBanner />
        <Outlet />
      </main>
    </div>
  );
}
