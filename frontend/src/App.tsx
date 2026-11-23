import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import type { Role } from './api/types';
import { Layout } from './components/Layout';
import { Loading } from './components/ui';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { AccountsPage } from './pages/AccountsPage';
import { AccountDetailPage } from './pages/AccountDetailPage';
import { TransferPage } from './pages/TransferPage';
import { ReceiptPage } from './pages/ReceiptPage';
import { CustomersPage } from './pages/CustomersPage';
import { AuditPage } from './pages/AuditPage';

function RequireAuth({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const { user, ready, can } = useAuth();
  const location = useLocation();
  if (!ready) return <Loading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  if (roles && !can(...roles)) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth><Layout /></RequireAuth>}>
            <Route index element={<DashboardPage />} />
            <Route path="cuentas" element={<AccountsPage />} />
            <Route path="cuentas/:id" element={<AccountDetailPage />} />
            <Route path="transferir" element={<RequireAuth roles={['CUSTOMER']}><TransferPage /></RequireAuth>} />
            <Route path="operaciones" element={<ReceiptPage />} />
            <Route path="operaciones/:reference" element={<ReceiptPage />} />
            <Route path="clientes" element={<RequireAuth roles={['OPERATOR', 'ADMIN']}><CustomersPage /></RequireAuth>} />
            <Route path="auditoria" element={<RequireAuth roles={['ADMIN']}><AuditPage /></RequireAuth>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
